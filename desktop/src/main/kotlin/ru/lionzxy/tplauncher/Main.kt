package ru.lionzxy.tplauncher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.sentry.Sentry
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.ui.LauncherViewModel
import ru.lionzxy.tplauncher.ui.components.Avatar
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

fun main() {
    // 1. UA first — must be before any HTTP connection
    configureHttpUserAgent()

    // 2. Sentry init
    Sentry.init { options ->
        options.dsn = BuildConfig.SENTRY_DSN
        options.serverName = BuildConfig.NAME
        options.release = BuildConfig.VERSION
        options.setTag("version", BuildConfig.VERSION)
    }

    // 3. Prepare logo on disk (no-op if already exists)
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

        // ── Main window ───────────────────────────────────────────────────────
        Window(
            onCloseRequest = ::exitApplication,
            undecorated = true,
            resizable = false,
            state = rememberWindowState(size = DpSize(592.dp, Dp.Unspecified)),
            icon = painterResource("icon/logo.png"),
            title = "TechnoparkLauncher",
        ) {
            // Drag layer: wraps the entire window background so dead-space is draggable.
            // Interactive widgets (fields, combo, buttons, close-X) consume pointer
            // events themselves and are laid out on top via Box z-order in
            // MainWindowContent — they are NOT inside a separate WindowDraggableArea
            // call, so their clicks are not swallowed.
            WindowDraggableArea {
                TpTheme {
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
                        ),
                        avatar = { Avatar() },
                    )
                }
            }
        }

        // ── Settings window (state-driven) ────────────────────────────────────
        if (showSettings) {
            Window(
                onCloseRequest = { showSettings = false },
                undecorated = true,
                resizable = false,
                state = rememberWindowState(size = DpSize(592.dp, Dp.Unspecified)),
                icon = painterResource("icon/logo.png"),
                title = "TechnoparkLauncher — Settings",
            ) {
                TpTheme {
                    SettingsWindowContent(
                        vm = remember {
                            SettingsViewModel(
                                settings = Settings(ConfigHelper.config.settings),
                                persist = { s -> ConfigHelper.writeToConfig { settings = s } },
                                onClose = { showSettings = false },
                                onExitApp = ::exitApplication,
                            )
                        },
                    )
                }
            }
        }
    }
}
