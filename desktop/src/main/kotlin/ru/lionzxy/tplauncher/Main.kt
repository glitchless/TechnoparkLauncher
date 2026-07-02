package ru.lionzxy.tplauncher

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.sentry.Sentry
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.ui.LauncherViewModel
import ru.lionzxy.tplauncher.ui.components.Avatar
import ru.lionzxy.tplauncher.ui.components.LogPanel
import ru.lionzxy.tplauncher.ui.settings.SettingsViewModel
import ru.lionzxy.tplauncher.ui.settings.SettingsWindowContent
import ru.lionzxy.tplauncher.ui.state.flags
import ru.lionzxy.tplauncher.ui.theme.TpTheme
import ru.lionzxy.tplauncher.ui.window.MainCallbacks
import ru.lionzxy.tplauncher.ui.window.MainWindowContent
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
import ru.lionzxy.tplauncher.utils.configureHttpUserAgent
import java.awt.Desktop
import java.net.URI

/** Design width of both windows in dp at scale x1; the window grows by [uiScale]. */
private const val BASE_WIDTH_DP = 592f

fun main() {
    // 1. UA first — must be before any HTTP connection
    configureHttpUserAgent()

    // 2. Sentry init — before the first log line, so that if opening the session log
    //    fails (e.g. an unwritable or full logs dir) the crash is still reported.
    Sentry.init { options ->
        options.dsn = BuildConfig.SENTRY_DSN
        options.serverName = BuildConfig.NAME
        options.release = BuildConfig.VERSION
        options.setTag("version", BuildConfig.VERSION)
    }

    // 3. Version banner — the first record in the session log. As the first Logger call
    //    it lazily creates the logs dir and opens the per-launch log file, so it is kept
    //    after Sentry.init (above) rather than first.
    AppInfo.logStartup()

    // 4. Prepare logo on disk (no-op if already exists)
    LogoUtils.prepareLogo()

    // 4. Launch Compose application
    application {
        val scope = rememberCoroutineScope()
        val vm = remember { LauncherViewModel(scope) }

        LaunchedEffect(Unit) { vm.onInitView() }

        val state by vm.state.collectAsState()
        val progress by vm.progress.collectAsState()

        // Quit when Minecraft has launched (MinecraftLaunched state sets isOpen=false)
        if (!state.flags.isOpen) exitApplication()

        // UI state owned here — combo selection must be observable (StateFlow no-op guard)
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var showSettings by remember { mutableStateOf(false) }
        var selectedModpack by remember { mutableStateOf(ConfigHelper.config.currentModpack) }

        // Whole-UI scale factor (x0.5 .. x16), chosen in Settings. Applied by overriding
        // LocalDensity below so layout AND text scale together; persisted in Config.
        var uiScale by remember { mutableStateOf(ConfigHelper.config.uiScale) }

        // Whether the in-window log panel is shown. Mirrors Settings.enableLogView and is
        // re-read whenever the settings window closes (apply() persists the new value first).
        var showLogView by remember { mutableStateOf(ConfigHelper.config.settings.enableLogView) }
        LaunchedEffect(showSettings) {
            if (!showSettings) showLogView = ConfigHelper.config.settings.enableLogView
        }

        // ── Main window ───────────────────────────────────────────────────────
        val mainWindowState = rememberWindowState(size = DpSize((BASE_WIDTH_DP * uiScale).dp, Dp.Unspecified))
        Window(
            onCloseRequest = ::exitApplication,
            undecorated = true,
            resizable = false,
            state = mainWindowState,
            icon = painterResource("icon/logo.png"),
            title = "TechnoparkLauncher",
        ) {
            // The window's true (OS) density, before our UI-scale override — used to convert
            // the measured content px back into window dp.
            val systemDensity = LocalDensity.current
            // Drag layer wraps the window background; interactive widgets consume their
            // own pointer events so their clicks aren't swallowed.
            WindowDraggableArea {
                // Scale the WHOLE UI (layout + text) by overriding the density.
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density * uiScale, systemDensity.fontScale),
                ) {
                    TpTheme {
                        // Fit the window to the (scaled) content: width = design·scale, height =
                        // measured content height. States differ in height — this is the
                        // sizeToScene() equivalent, so a shorter state leaves no gap.
                        Box(
                            // unbounded=true: measure the content's TRUE height even when it
                            // exceeds the current window, so scaling up actually grows the window.
                            modifier = Modifier
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .onSizeChanged { size ->
                                    val target = DpSize(
                                        (BASE_WIDTH_DP * uiScale).dp,
                                        with(systemDensity) { size.height.toDp() },
                                    )
                                    if (target.height > 0.dp && mainWindowState.size != target) {
                                        mainWindowState.size = target
                                    }
                                },
                        ) {
                        MainWindowContent(
                            state = state,
                            progress = progress,
                            email = email,
                            password = password,
                            serverItems = MinecraftModpack.values().map { it.modpackName },
                            selectedServer = MinecraftModpack.values().indexOf(selectedModpack),
                            callbacks = MainCallbacks(
                                onButtonClick = { e, p -> vm.onButtonClick(e, p) },
                                onEmailChange = { email = it; vm.onPasswordOrLoginChange() },
                                onPasswordChange = { password = it; vm.onPasswordOrLoginChange() },
                                onModpackSelect = { i ->
                                    selectedModpack = MinecraftModpack.values()[i]
                                    vm.onChangeModpack(selectedModpack)
                                },
                                onRegisterClick = {
                                    if (Desktop.isDesktopSupported() &&
                                        Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
                                    ) {
                                        Desktop.getDesktop().browse(URI("https://games.glitchless.ru/register/"))
                                    }
                                },
                                onSettingsClick = { showSettings = true },
                                onCloseClick = ::exitApplication,
                                onConnectivityFix = { vm.onConnectivityFix() },
                                onConnectivityRetry = { vm.onConnectivityRetry() },
                            ),
                            avatar = { Avatar() },
                            logView = { if (showLogView) LogPanel(window) },
                        )
                        }
                    }
                }
            }
        }

        // ── Settings window (state-driven) ────────────────────────────────────
        if (showSettings) {
            val settingsWindowState = rememberWindowState(size = DpSize((BASE_WIDTH_DP * uiScale).dp, Dp.Unspecified))
            Window(
                onCloseRequest = { showSettings = false },
                undecorated = true,
                resizable = false,
                state = settingsWindowState,
                icon = painterResource("icon/logo.png"),
                title = "TechnoparkLauncher — Settings",
            ) {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density * uiScale, systemDensity.fontScale),
                ) {
                    TpTheme {
                        Box(
                            modifier = Modifier
                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                .onSizeChanged { size ->
                                    val target = DpSize(
                                        (BASE_WIDTH_DP * uiScale).dp,
                                        with(systemDensity) { size.height.toDp() },
                                    )
                                    if (target.height > 0.dp && settingsWindowState.size != target) {
                                        settingsWindowState.size = target
                                    }
                                },
                        ) {
                            SettingsWindowContent(
                                vm = remember {
                                    SettingsViewModel(
                                        settings = Settings(ConfigHelper.config.settings),
                                        persist = { s -> ConfigHelper.writeToConfig { settings = s } },
                                        onClose = { showSettings = false },
                                        onExitApp = ::exitApplication,
                                    )
                                },
                                currentScale = uiScale,
                                onScaleChange = { newScale ->
                                    uiScale = newScale
                                    // `this.` is REQUIRED: an unqualified `uiScale` here binds to the
                                    // outer Compose state var, not Config.uiScale, so it would never persist.
                                    ConfigHelper.writeToConfig { this.uiScale = newScale }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
