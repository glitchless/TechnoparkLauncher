package ru.lionzxy.tplauncher.minecraft.connectivity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketException

class KtorConnectivityProbeTest {
    private val url = "https://minecraft.glitchless.ru/jres2.json"

    private fun probe(engine: MockEngine, expectSuccess: Boolean = false) =
        KtorConnectivityProbe(HttpClient(engine) { this.expectSuccess = expectSuccess }, url)

    @Test
    fun anyHttpResponseIsReachable() = runBlocking {
        // Even a 403 means the socket opened and a response came back.
        val p = probe(MockEngine { respond("nope", HttpStatusCode.Forbidden) })
        assertEquals(ProbeResult.REACHABLE, p.probe())
    }

    @Test
    fun httpErrorStatusIsReachableEvenWithExpectSuccess() = runBlocking {
        // With expectSuccess=true a 403 throws a Ktor ResponseException; a response still arrived → reachable.
        val p = probe(MockEngine { respond("nope", HttpStatusCode.Forbidden) }, expectSuccess = true)
        assertEquals(ProbeResult.REACHABLE, p.probe())
    }

    @Test
    fun permissionDeniedIsBlocked() = runBlocking {
        val p = probe(MockEngine { throw SocketException("Permission denied: connect") })
        assertEquals(ProbeResult.BLOCKED, p.probe())
    }

    @Test
    fun otherErrorIsOtherFailure() = runBlocking {
        val p = probe(MockEngine { throw SocketException("Connection reset") })
        assertEquals(ProbeResult.OTHER_FAILURE, p.probe())
    }
}
