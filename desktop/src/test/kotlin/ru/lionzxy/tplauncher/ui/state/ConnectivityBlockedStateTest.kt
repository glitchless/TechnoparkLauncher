package ru.lionzxy.tplauncher.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
