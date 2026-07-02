package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
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
    fun detectsBlockHiddenInYdAuthExceptionThrownField() {
        // mclauncher-api's YDLoginService wraps a login-time SocketException into
        // YDServiceAuthenticationException, whose constructor calls super(msg) ONLY — the real cause
        // is parked in its non-standard `thrown` field, so getCause() is null. The classifier must
        // still recognise the WSAEACCES block through that field, otherwise a login-time Dr.Web block
        // is misreported as a generic "Failed to authenticate" error.
        val block = YDServiceAuthenticationException(
            "Failed to authenticate using Mojang authentication service.",
            SocketException("Permission denied: getsockopt"),
        )
        assertNull("precondition: YDServiceAuthenticationException hides its cause, so getCause() is null", block.cause)
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(block))
        assertTrue(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(block))
    }

    @Test
    fun ydAuthExceptionWrappingA403IsNotABlock() {
        // A rejected login (HTTP 401/403) wraps a plain IOException, not a SocketException, so it must
        // NOT be misread as a firewall/AV block even though it flows through the same `thrown` field.
        val rejected = YDServiceAuthenticationException(
            "Failed to authenticate using Mojang authentication service.",
            IOException("Server returned HTTP response code: 403 for URL: https://games.glitchless.ru"),
        )
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(rejected))
        assertFalse(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(rejected))
    }

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

    @Test
    fun digitsResemblingWsaCodeInsideAnAddressAreNotABlock() {
        // "10013" as a bare digit sequence appears in ports/hosts/byte counts; matching it would
        // send a plain reachability failure into the firewall-repair flow.
        assertFalse(
            ConnectivityBlockClassifier.isPermissionDeniedSocket(
                java.net.ConnectException("Connect to proxy 10.0.0.1:10013 timed out"),
            ),
        )
    }

    @Test
    fun environmentalFailuresAreClassifiedAsSuch() {
        assertTrue(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(UnknownHostException("host")))
        assertTrue(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(java.net.ConnectException("Connection refused: connect")))
        assertTrue(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(java.net.SocketTimeoutException("connect timed out")))
        assertTrue(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(javax.net.ssl.SSLException("handshake refused")))
        assertTrue(
            "wrapped causes must be found",
            ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(
                RuntimeException("prepare", IOException("io", SocketException("Permission denied: connect"))),
            ),
        )
    }

    @Test
    fun genuineErrorsAreNotEnvironmental() {
        assertFalse(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(IOException("Server returned HTTP response code: 500")))
        assertFalse(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(IllegalStateException("boom")))
        assertFalse(ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(null))
    }
}
