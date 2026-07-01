package ru.lionzxy.tplauncher.minecraft.connectivity

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.head
import ru.lionzxy.tplauncher.log.Logger

enum class ProbeResult { REACHABLE, BLOCKED, OTHER_FAILURE }

interface ConnectivityProbe {
    suspend fun probe(): ProbeResult
}

/**
 * Opens a socket to [url]; ANY HTTP response — even a 4xx/5xx — means the connection succeeded, so it
 * maps to [ProbeResult.REACHABLE]. A WSAEACCES connect exception → [ProbeResult.BLOCKED]; any other
 * failure → [ProbeResult.OTHER_FAILURE].
 *
 * Inject a short-timeout [client] (production wiring uses a dedicated CIO client with an ~8s connect/
 * request cap so a probe can't inherit the downloader's 5-minute window). The probe is robust to the
 * client's `expectSuccess` setting: a non-2xx surfaces as a Ktor [ResponseException], which still means
 * a response arrived → reachable.
 */
class KtorConnectivityProbe(private val client: HttpClient, private val url: String) : ConnectivityProbe {
    override suspend fun probe(): ProbeResult = try {
        client.head(url)
        ProbeResult.REACHABLE
    } catch (e: ResponseException) {
        ProbeResult.REACHABLE // an HTTP status came back → the socket opened
    } catch (e: Throwable) {
        if (ConnectivityBlockClassifier.isPermissionDeniedSocket(e)) {
            ProbeResult.BLOCKED
        } else {
            Logger.w("Connectivity", "Probe to $url failed (non-permission)", e)
            ProbeResult.OTHER_FAILURE
        }
    }
}
