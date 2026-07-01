package ru.lionzxy.tplauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.state.LauncherState
import java.net.SocketException
import java.net.UnknownHostException

class LaunchErrorMapperTest {
    @Test
    fun permissionDeniedMapsToConnectionBlockedNoSentry() {
        val m = mapLaunchError("a@b.c", SocketException("Permission denied: connect"))
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.connectionBlocked), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test
    fun unknownHostMapsToCheckInternetNoSentry() {
        val m = mapLaunchError("a@b.c", UnknownHostException("host"))
        assertEquals(LauncherState.InitialError(Strings.checkInternetConnection), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test
    fun otherErrorsMapToInternalAndReport() {
        val m = mapLaunchError("a@b.c", IllegalStateException("boom"))
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.internalError), m.state)
        assertTrue(m.reportToSentry)
    }

    @Test
    fun permissionDeniedWrappedInCauseChainIsDetected() {
        val wrapped = RuntimeException("prepare", SocketException("Permission denied: getsockopt"))
        val m = mapLaunchError("a@b.c", wrapped)
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.connectionBlocked), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test
    fun plainOfflineFailuresAreEnvironmentalNotInternal() {
        // A refused/timed-out connection is the user's network, not a launcher bug: actionable
        // message, no Sentry report.
        val m = mapLaunchError("a@b.c", java.net.ConnectException("Connection refused: connect"))
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.checkInternetConnection), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test
    fun notInstalledOfflineGetsItsOwnMessageNoSentry() {
        val e = ru.lionzxy.tplauncher.minecraft.connectivity.VersionNotInstalledOfflineException(
            "1.7.10",
            java.net.ConnectException("Connection refused: connect"),
        )
        val m = mapLaunchError("a@b.c", e)
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.notInstalledOffline), m.state)
        assertFalse(m.reportToSentry)
    }
}
