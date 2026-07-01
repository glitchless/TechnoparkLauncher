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
}
