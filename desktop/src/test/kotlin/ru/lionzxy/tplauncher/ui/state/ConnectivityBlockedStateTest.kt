package ru.lionzxy.tplauncher.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.theme.TpColors

class ConnectivityBlockedStateTest {
    @Test
    fun showsMessageAndEnablesButton() {
        val s = LauncherState.ConnectivityBlocked("a@b.c", "allow me in Dr.Web", canFirewallFix = false)
        val f = s.flags
        assertEquals("allow me in Dr.Web", f.progressTextContent)
        assertFalse("the retry/fix button must be enabled", f.buttonDisable)
        assertEquals(TpColors.error, f.titleColor)
        assertEquals("a@b.c", f.successLoginText)
    }

    @Test
    fun firewallFixButtonTextWhenFixable() {
        val s = LauncherState.ConnectivityBlocked("a@b.c", "msg", canFirewallFix = true)
        assertEquals(Strings.allowAccess, s.flags.buttonText)
    }

    @Test
    fun retryButtonTextWhenNotFixable() {
        val s = LauncherState.ConnectivityBlocked("a@b.c", "msg", canFirewallFix = false)
        assertEquals(Strings.retry, s.flags.buttonText)
    }

    @Test
    fun launchOriginShowsLoggedInAvatarNotLoginFields() {
        // Default origin is LAUNCH (block during an authenticated launch): the logged-in avatar block
        // shows and the login/password fields stay hidden — unchanged from before.
        val f = LauncherState.ConnectivityBlocked("a@b.c", "msg", canFirewallFix = false).flags
        assertFalse("launch-origin block must hide the login/password fields", f.loginPasswordVisible)
        assertTrue("launch-origin block must show the logged-in avatar", f.successLoginVisible)
    }

    @Test
    fun loginOriginShowsLoginFieldsNotAvatar() {
        // A pre-login block has no session/avatar, so the login/password fields must stay visible for
        // the user to adjust credentials and re-authenticate. Showing the "logged in" avatar here would
        // be misleading, and the retry must route to login (not the session-dereferencing launch path).
        val f = LauncherState.ConnectivityBlocked(
            "a@b.c", "msg", canFirewallFix = false, origin = ConnectivityBlockOrigin.LOGIN,
        ).flags
        assertTrue("login-origin block must show the login/password fields", f.loginPasswordVisible)
        assertFalse("login-origin block must not show the logged-in avatar", f.successLoginVisible)
    }
}
