package ru.lionzxy.tplauncher.ui.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.AvatarContent
import ru.lionzxy.tplauncher.ui.components.CloseX
import ru.lionzxy.tplauncher.ui.components.GearRow
import ru.lionzxy.tplauncher.ui.components.ProgressPanel
import ru.lionzxy.tplauncher.ui.components.RegisterLink
import ru.lionzxy.tplauncher.ui.components.Title
import ru.lionzxy.tplauncher.ui.components.TpButton
import ru.lionzxy.tplauncher.ui.components.TpServerCombo
import ru.lionzxy.tplauncher.ui.components.TpTextField
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.ui.state.flags
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

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
)

/**
 * The main window content composable — pure and stateless.
 *
 * Every widget is driven declaratively from [state].flags + [progress].
 * Window chrome (drag, decoration) is handled by Task 11; this is pure content.
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
            // top=11.5dp, left=23dp  (no explicit bottom/right padding here)
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
                        TpTextField(
                            value = email,
                            onValueChange = callbacks.onEmailChange,
                            label = Strings.login,
                            enabled = !disableInputField,
                        )
                        Spacer(modifier = Modifier.size(TpDimens.margin))
                        TpTextField(
                            value = password,
                            onValueChange = callbacks.onPasswordChange,
                            label = Strings.password,
                            password = true,
                            enabled = !disableInputField,
                        )
                    } else if (flags.successLoginVisible) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            avatar()
                            Spacer(modifier = Modifier.width(TpDimens.margin))
                            Column {
                                BasicText(
                                    text = flags.successLoginText,
                                    style = TpTypography.body,
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

                // RIGHT column: Server label + combo + GearRow ────────────────
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = Strings.server,
                        style = TpTypography.body,
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    TpServerCombo(
                        items = serverItems,
                        selectedIndex = selectedServer,
                        onSelect = callbacks.onModpackSelect,
                        enabled = !flags.disableSelectModpack,
                    )
                    Spacer(modifier = Modifier.size(TpDimens.margin))
                    GearRow(
                        enabled = flags.settingsFieldIsClickable,
                        onClick = callbacks.onSettingsClick,
                    )
                }
            }

            // ── Register link (conditional) ────────────────────────────────────
            // right=16dp, bottom=16dp, left=23dp
            if (flags.registerFieldIsVisible) {
                RegisterLink(
                    color = flags.registerFieldColor,
                    onClick = callbacks.onRegisterClick,
                    modifier = Modifier.padding(
                        start = TpDimens.gutter,
                        end = TpDimens.margin,
                        bottom = TpDimens.margin,
                        top = TpDimens.margin,
                    ),
                )
            }

            // ── Launch button ──────────────────────────────────────────────────
            // left=16dp, right=16dp, bottom=16dp
            TpButton(
                text = flags.buttonText,
                enabled = !flags.buttonDisable,
                onClick = { callbacks.onButtonClick(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TpDimens.margin, end = TpDimens.margin, bottom = TpDimens.margin),
            )

            // ── Progress panel ─────────────────────────────────────────────────
            ProgressPanel(
                text = progress.status ?: flags.progressTextContent ?: "",
                textColor = flags.progressTextColor,
                value = progress.value,
                enabled = !flags.disableProgressBar,
            )
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
