package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.lionzxy.tplauncher.log.LogLevel
import ru.lionzxy.tplauncher.log.LogLine
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography
import java.awt.FileDialog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// How many records make up one lazily-loaded page read from the log file.
private const val PAGE = 128

// Fixed viewport height — about four lines of the small monospace log font (per spec).
private val LOG_VIEW_HEIGHT = 88.dp

// Header action-icon size (copy / save / jump-to-bottom).
private val LOG_ICON_SIZE = 16.dp

private val WarnColor = Color(0xFFE0A030)

/**
 * Scrollable, selectable view of the current session log, shown under the main-window
 * content when `Settings.enableLogView` is on.
 *
 * The log file (see [Logger]) is the source of truth. This panel never holds the whole
 * log in memory: it sizes a [LazyColumn] to [Logger.count] and loads only the visible
 * ±1 pages from disk via [Logger.read] on [Dispatchers.IO], evicting far pages. Records
 * not yet loaded render as blank rows and fill in on the next recompose.
 *
 * - **Selectable:** wrapped in a [SelectionContainer] (manual select + Ctrl+C of visible rows).
 * - **Copy all:** [Logger.readAll] → clipboard (the whole file, not just the cached window).
 * - **Save:** native [FileDialog] (SAVE) → copies the session file to the chosen path.
 * - **Autoscroll:** follows new entries only while the view is already pinned to the bottom.
 *
 * @param window the host [ComposeWindow] — parent for the native save dialog.
 */
@Composable
fun LogPanel(window: ComposeWindow, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val countState = Logger.count.collectAsState()
    val total = countState.value

    val listState = rememberLazyListState()
    // page index -> records of that page (each record's text may span multiple lines).
    val pageCache = remember { mutableStateMapOf<Int, List<LogLine>>() }

    val monoStyle = remember {
        TpTypography.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }

    // ── Windowed load from file: react to the visible range AND to new entries ──
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Reading countState.value here makes the flow re-emit when new entries arrive.
            Triple(first, last, countState.value)
        }.distinctUntilChanged().collect { (first, last, count) ->
            if (count <= 0) {
                pageCache.clear()
                return@collect
            }
            val firstPage = maxOf(0, first - 1) / PAGE
            val lastPage = minOf(count - 1, last + 1) / PAGE
            val tailPage = (count - 1) / PAGE
            for (p in firstPage..lastPage) {
                // The tail page grows as entries are appended; reload it whenever its
                // expected length no longer matches what we cached.
                val expected = if (p == tailPage) count - p * PAGE else PAGE
                val cached = pageCache[p]
                if (cached == null || cached.size != expected) {
                    val lines = withContext(Dispatchers.IO) { Logger.read(p * PAGE, PAGE) }
                    pageCache[p] = lines
                }
            }
            // Bound memory: drop pages outside the visible window (± one page).
            val keepMin = firstPage - 1
            val keepMax = lastPage + 1
            pageCache.keys.toList().forEach { if (it < keepMin || it > keepMax) pageCache.remove(it) }
        }
    }

    // ── Autoscroll: a user-toggled "follow tail" mode driven by the ↓ button ──
    // While `follow` is on, every new entry (and toggling it on) scrolls to the latest line.
    // A single long-lived collector keyed on `listState` (NOT on the changing count) so a
    // burst of appends — thousands of per-file download lines — can't tear the effect down and
    // cancel the scroll mid-flight; `collectLatest` always lands on the newest record. `follow`
    // is a manual toggle, so it never flips off when new items append (the bug that stopped the
    // earlier "is the last item visible?" gate from ever following a burst).
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { countState.value to follow }
            .collectLatest { (count, isFollowing) ->
                if (isFollowing && count > 0) listState.scrollToItem(count - 1)
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TpColors.backgroundDark)
            .padding(horizontal = TpDimens.margin, vertical = 8.dp),
    ) {
        // ── Header: title + copy/save actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = Strings.logsTitle, style = TpTypography.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Toggle: active (accent) follows the tail and autoscrolls; inactive (grey)
                // freezes the view so the user can read back through history.
                IconActionButton(
                    icon = TpIcons.Chevron,
                    contentDescription = Strings.scrollToEnd,
                    tint = if (follow) TpColors.accent else TpColors.disable,
                ) { follow = !follow }
                IconActionButton(TpIcons.Copy, Strings.copyLogs) {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { Logger.readAll() }
                        clipboard.setText(AnnotatedString(text))
                    }
                }
                IconActionButton(TpIcons.Save, Strings.saveLogs) {
                    // The native modal dialog runs on the calling (Compose/EDT) thread; the
                    // file copy is dispatched to IO once a destination is chosen.
                    val dialog = FileDialog(window, Strings.saveLogDialogTitle, FileDialog.SAVE)
                    dialog.file = defaultLogFileName()
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    if (dir != null && name != null) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                Files.copy(
                                    Logger.logFile().toPath(),
                                    File(dir, name).toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                )
                            }.onFailure { Logger.e("LogPanel", "Failed to save log to $dir$name", it) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Scrollable log area (fixed height ≈ 4 lines) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LOG_VIEW_HEIGHT)
                .background(TpColors.background),
        ) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    items(total) { index ->
                        // The async page cache may not have caught up to a just-appended tail
                        // record yet; fall back to the synchronous in-memory tail so the newest
                        // line (the one being followed) never flashes blank.
                        val line = pageCache[index / PAGE]?.getOrNull(index % PAGE)
                            ?: Logger.peekRecent(index)
                        BasicText(
                            text = line?.text ?: "",
                            style = monoStyle.copy(color = colorFor(line?.level)),
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = TpColors.accent,
    onClick: () -> Unit,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier
            .size(LOG_ICON_SIZE)
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand),
    )
}

private fun colorFor(level: LogLevel?): Color = when (level) {
    LogLevel.ERROR -> TpColors.error
    LogLevel.WARN -> WarnColor
    else -> TpColors.text
}

private fun defaultLogFileName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    return "technopark-launcher-log-$stamp.txt"
}
