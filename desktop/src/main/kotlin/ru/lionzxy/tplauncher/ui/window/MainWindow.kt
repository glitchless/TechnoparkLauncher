package ru.lionzxy.tplauncher.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.AvatarContent
import ru.lionzxy.tplauncher.ui.components.CloseX
import ru.lionzxy.tplauncher.ui.components.ConnectivityRepairPanel
import ru.lionzxy.tplauncher.ui.components.GearRow
import ru.lionzxy.tplauncher.ui.components.ProgressPanel
import ru.lionzxy.tplauncher.ui.components.RegisterLink
import ru.lionzxy.tplauncher.ui.components.Title
import ru.lionzxy.tplauncher.ui.components.TpButton
import ru.lionzxy.tplauncher.ui.components.TpField
import ru.lionzxy.tplauncher.ui.components.TpServerCombo
import ru.lionzxy.tplauncher.ui.components.TpTextField
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.ui.state.flags
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

// Label column width for main-window form rows ("Логин", "Пароль", "Сервер" all fit in 90dp)
private val MAIN_LABEL_WIDTH = 90.dp

// Row-to-row vertical gap inside the form (matches legacy ~16dp field padding)
private val FIELD_ROW_GAP = 16.dp

/**
 * All event callbacks for [MainWindowContent].
 * All default to no-ops so previews/tests can omit them.
 */
data class MainCallbacks(
    val onButtonClick: (email: String, password: String) -> Unit = { _, _ -> },
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onModpackSelect: (Int) -> Unit = {},
    val onRegisterClick: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onCloseClick: () -> Unit = {},
    // ConnectivityBlocked repair panel actions. The (email, password) are the current login-field
    // values, needed to re-authenticate a login-origin block (ignored for a launch-origin one).
    val onConnectivityFix: (email: String, password: String) -> Unit = { _, _ -> },
    val onConnectivityRetry: (email: String, password: String) -> Unit = { _, _ -> },
)

/**
 * The main window content composable — pure and stateless.
 *
 * Every widget is driven declaratively from [state].flags + [progress].
 * Window chrome (drag, decoration) is handled by Task 11; this is pure content.
 *
 * Layout matches Screen 1/2/4:
 *   Two-column grid:
 *     LEFT  — [Логин label | field] over [Пароль label | field]   (or avatar block)
 *     RIGHT — [Сервер label | combo] over [gear icon | Настройки запуска]
 *
 * @param avatar  Production: [ru.lionzxy.tplauncher.ui.components.Avatar] (reads config/net).
 *                Snapshots: [AvatarContent](null) for deterministic rendering.
 */
@Composable
fun MainWindowContent(
    state: LauncherState,
    progress: ProgressUiState,
    email: String = "",
    password: String = "",
    serverItems: List<String> = listOf("Vanilla", "NewHorizon", "Nomifactory"),
    selectedServer: Int = 0,
    callbacks: MainCallbacks = MainCallbacks(),
    avatar: @Composable () -> Unit = { AvatarContent(null) },
    logView: @Composable () -> Unit = {},
) {
    val flags = state.flags
    val disableInputField = flags.disableInputField

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TpColors.background),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Title ──────────────────────────────────────────────────────────
            // top=11.5dp, left=23dp
            Title(
                color = flags.titleColor,
                modifier = Modifier.padding(top = TpDimens.titleTop, start = TpDimens.gutter),
            )

            // ── Form region ────────────────────────────────────────────────────
            // top=16dp, right=16dp, left=23dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TpDimens.margin, end = TpDimens.margin, start = TpDimens.gutter),
                verticalAlignment = Alignment.Top,
            ) {
                // LEFT column: login+password OR avatar+name ─────────────────
                Column(modifier = Modifier.weight(1f)) {
                    if (flags.loginPasswordVisible) {
                        TpField(
                            label = Strings.login,
                            labelWidth = MAIN_LABEL_WIDTH,
                            enabled = !disableInputField,
                        ) {
                            TpTextField(
                                value = email,
                                onValueChange = callbacks.onEmailChange,
                                enabled = !disableInputField,
                            )
                        }
                        Spacer(modifier = Modifier.height(FIELD_ROW_GAP))
                        TpField(
                            label = Strings.password,
                            labelWidth = MAIN_LABEL_WIDTH,
                            enabled = !disableInputField,
                        ) {
                            TpTextField(
                                value = password,
                                onValueChange = callbacks.onPasswordChange,
                                password = true,
                                enabled = !disableInputField,
                            )
                        }
                    } else if (flags.successLoginVisible) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            avatar()
                            Spacer(modifier = Modifier.width(TpDimens.margin))
                            Column {
                                BasicText(
                                    text = flags.successLoginText,
                                    style = TpTypography.body,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                                BasicText(
                                    text = Strings.loggedIn,
                                    style = TpTypography.caption,
                                )
                            }
                        }
                    }
                }

                // Column gap
                Spacer(modifier = Modifier.width(TpDimens.columnGap))

                // RIGHT column: Server (label-left) + GearRow ────────────────
                Column(modifier = Modifier.weight(1f)) {
                    TpField(
                        label = Strings.server,
                        labelWidth = MAIN_LABEL_WIDTH,
                    ) {
                        TpServerCombo(
                            items = serverItems,
                            selectedIndex = selectedServer,
                            onSelect = callbacks.onModpackSelect,
                            enabled = !flags.disableSelectModpack,
                        )
                    }
                    Spacer(modifier = Modifier.height(FIELD_ROW_GAP))
                    GearRow(
                        enabled = flags.settingsFieldIsClickable,
                        onClick = callbacks.onSettingsClick,
                    )
                }
            }

            // ── Repair panel (firewall/AV block) replaces the button/progress region ──
            // The form above stays: the user keeps the modpack combo and settings gear even
            // while blocked (switching packs / tweaking settings may be their way out).
            if (state is LauncherState.ConnectivityBlocked) {
                ConnectivityRepairPanel(
                    message = state.message,
                    canFirewallFix = state.canFirewallFix,
                    onFix = { callbacks.onConnectivityFix(email, password) },
                    onRetry = { callbacks.onConnectivityRetry(email, password) },
                )
            } else {

            // ── Register link (conditional) ────────────────────────────────────
            // right=16dp, bottom=16dp, left=23dp
            if (flags.registerFieldIsVisible) {
                RegisterLink(
                    color = flags.registerFieldColor,
                    onClick = callbacks.onRegisterClick,
                    modifier = Modifier.padding(
                        start = TpDimens.gutter,
                        end = TpDimens.margin,
                        top = TpDimens.margin,
                    ),
                )
            }

            // ── Launch button ──────────────────────────────────────────────────
            // top margin is always present so the button keeps a consistent gap below the
            // form in EVERY state (when the register link is hidden it used to hug the gear row).
            TpButton(
                text = flags.buttonText,
                enabled = !flags.buttonDisable,
                onClick = { callbacks.onButtonClick(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TpDimens.margin,
                        end = TpDimens.margin,
                        top = TpDimens.margin,
                        bottom = TpDimens.margin,
                    ),
            )

            // ── Progress panel ─────────────────────────────────────────────────
            // While the bar is live, the streaming download status leads; in static/error states
            // the state's own text must win — otherwise a stale "Installing …" status masks the
            // error message the state carries.
            ProgressPanel(
                text = if (flags.disableProgressBar) {
                    flags.progressTextContent ?: progress.status ?: ""
                } else {
                    progress.status ?: flags.progressTextContent ?: ""
                },
                textColor = flags.progressTextColor,
                value = progress.value,
                enabled = !flags.disableProgressBar,
            )
            } // end: button/progress region vs ConnectivityBlocked repair panel

            // ── Log view (optional; gated on Settings.enableLogView, injected by Main) ──
            logView()
        }

        // ── Close X (TopEnd overlay) ───────────────────────────────────────────
        CloseX(
            onClick = callbacks.onCloseClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(TpDimens.margin),
        )
    }
}
