package ru.lionzxy.tplauncher.snapshot

import androidx.compose.runtime.Composable
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.AvatarContent
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.ui.theme.TpTheme
import ru.lionzxy.tplauncher.ui.window.MainWindowContent

class MainWindowSnapshotTest {

    private fun snap(
        name: String,
        h: Int,
        state: LauncherState,
        progress: ProgressUiState = ProgressUiState(),
    ) = snapshot(name, 960, h) {
        TpTheme {
            MainWindowContent(
                state = state,
                progress = progress,
                email = "st3althtech@mail.ru",
                avatar = { AvatarContent(null) },
            )
        }
    }

    @Test
    fun screen1_login() {
        val f = snap("login", 612, LauncherState.Initial)
        assertTrue("login snapshot must be non-empty", f.length() > 0)
    }

    @Test
    fun screen2_loggedIn() {
        val f = snap("loggedIn", 540, LauncherState.Logged("st3althtech@mail.ru"))
        assertTrue("loggedIn snapshot must be non-empty", f.length() > 0)
    }

    @Test
    fun screen3_loginProg() {
        val f = snap(
            "loginProg",
            540,
            LauncherState.LoginProgress,
            ProgressUiState(status = null, value = -1f),
        )
        assertTrue("loginProg snapshot must be non-empty", f.length() > 0)
    }

    @Test
    fun screen4_error() {
        val f = snap("error", 540, LauncherState.InitialError("Введите валидную почту"))
        assertTrue("error snapshot must be non-empty", f.length() > 0)
    }

    @Test
    fun screen5_connectivityBlocked_drweb() {
        val f = snap(
            "connectivityBlocked_drweb",
            760,
            LauncherState.ConnectivityBlocked(
                email = "st3althtech@mail.ru",
                message = Strings.drwebFirewallGuidance,
                canFirewallFix = false,
            ),
        )
        assertTrue("drweb connectivity-blocked snapshot must be non-empty", f.length() > 0)
    }

    @Test
    fun screen6_connectivityBlocked_firewall() {
        val f = snap(
            "connectivityBlocked_firewall",
            700,
            LauncherState.ConnectivityBlocked(
                email = "st3althtech@mail.ru",
                message = Strings.connectionBlocked,
                canFirewallFix = true,
            ),
        )
        assertTrue("firewall connectivity-blocked snapshot must be non-empty", f.length() > 0)
    }
}
