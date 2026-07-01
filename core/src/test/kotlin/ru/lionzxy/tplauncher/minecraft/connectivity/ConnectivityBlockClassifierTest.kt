package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class ConnectivityBlockClassifierTest {
    @Test
    fun detectsPermissionDeniedConnect() =
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Permission denied: connect")))

    @Test
    fun detectsPermissionDeniedGetsockopt() =
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Permission denied: getsockopt")))

    @Test
    fun detectsWhenWrappedInCauseChain() =
        assertTrue(
            ConnectivityBlockClassifier.isPermissionDeniedSocket(
                RuntimeException("prepare failed", IOException("io", SocketException("Permission denied: connect"))),
            ),
        )

    @Test
    fun ignoresUnknownHost() =
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(UnknownHostException("minecraft.glitchless.ru")))

    @Test
    fun ignoresOtherSocketErrors() =
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Connection reset")))

    @Test
    fun nullIsFalse() = assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(null))

    @Test
    fun guardsAgainstCyclicCauseChain() {
        val a = SocketException("Connection reset")
        val b = SocketException("also not a permission error")
        a.initCause(b)
        b.initCause(a) // a <-> b cycle must not loop forever
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(a))
    }
}
