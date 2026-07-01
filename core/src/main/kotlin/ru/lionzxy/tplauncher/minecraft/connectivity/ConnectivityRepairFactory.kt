package ru.lionzxy.tplauncher.minecraft.connectivity

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import ru.lionzxy.tplauncher.minecraft.JRES_JSON_LINK
import ru.lionzxy.tplauncher.minecraft.jre.JreManager
import ru.lionzxy.tplauncher.utils.HTTP_USER_AGENT
import java.io.File

/**
 * Assembles a production [ConnectivityRepairOrchestrator] with the real Windows implementations,
 * keeping all HTTP/JNA wiring in the core module. On non-Windows the impls are inert (the assistant
 * never triggers because WSAEACCES doesn't occur there).
 */
object ConnectivityRepair {
    private const val PROBE_TIMEOUT_MS = 8_000L

    fun forModpack(javaCode: String, scriptDir: File, probeUrl: String = JRES_JSON_LINK): ConnectivityRepairOrchestrator {
        val jreBinDir = runCatching { JreManager.instance.resolveJavaBinary(javaCode)?.parentFile }.getOrNull()
        return ConnectivityRepairOrchestrator(
            binaries = WindowsBinaryResolver(jreBinDir),
            runner = WindowsShellElevatedRunner(),
            probe = KtorConnectivityProbe(createProbeClient(), probeUrl),
            detector = WindowsWmiSecurityProductDetector(),
            scriptDir = scriptDir,
        )
    }

    /** A short-timeout client so a probe can't inherit the downloader's 5-minute window. */
    private fun createProbeClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false // any HTTP status = the socket opened = reachable
        install(UserAgent) { agent = HTTP_USER_AGENT }
        install(HttpTimeout) {
            connectTimeoutMillis = PROBE_TIMEOUT_MS
            requestTimeoutMillis = PROBE_TIMEOUT_MS
            socketTimeoutMillis = PROBE_TIMEOUT_MS
        }
    }
}
