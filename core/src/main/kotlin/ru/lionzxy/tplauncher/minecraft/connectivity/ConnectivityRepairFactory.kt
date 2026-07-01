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

    /**
     * One application-lifetime probe client (the same create-once-and-reuse pattern as
     * [ru.lionzxy.tplauncher.utils.HttpDownloader.instance]): orchestrators are rebuilt per modpack,
     * and a per-orchestrator CIO client would leak its engine threads — nothing ever closes it.
     * Deliberately NOT [ru.lionzxy.tplauncher.utils.applyDefaults]: the probe wants a short timeout,
     * no retries, and expectSuccess=false (any HTTP status = the socket opened = reachable), so it
     * can't inherit the downloader's 5-minute window.
     */
    private val probeClient: HttpClient by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            install(UserAgent) { agent = HTTP_USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = PROBE_TIMEOUT_MS
                requestTimeoutMillis = PROBE_TIMEOUT_MS
                socketTimeoutMillis = PROBE_TIMEOUT_MS
            }
        }
    }

    fun forModpack(javaCode: String, scriptDir: File, probeUrl: String = JRES_JSON_LINK): ConnectivityRepairOrchestrator {
        return ConnectivityRepairOrchestrator(
            // The bundled JRE is resolved lazily per fix attempt: at construction time (first launch
            // blocked before the JRE download) it may not exist yet.
            binaries = WindowsBinaryResolver {
                runCatching { JreManager.instance.resolveJavaBinary(javaCode)?.parentFile }.getOrNull()
            },
            runner = WindowsShellElevatedRunner(),
            probe = KtorConnectivityProbe(probeClient, probeUrl),
            detector = WindowsWmiSecurityProductDetector(),
            scriptDir = scriptDir,
        )
    }
}
