package ru.lionzxy.tplauncher.minecraft.connectivity

import kotlinx.coroutines.delay
import ru.lionzxy.tplauncher.log.Logger
import java.io.File

/** What the UI should lead with, decided by the cheap unprivileged [ConnectivityRepairOrchestrator.assess]. */
data class Assessment(val products: List<String>, val avClass: AvClass) {
    /** Derived, not stored: the firewall fix leads unless a third-party network AV owns the block. */
    val firewallFixFirst: Boolean get() = avClass != AvClass.THIRD_PARTY_NETWORK
}

sealed interface RepairOutcome {
    /** Probe is now reachable → caller retries the failed launch/login. */
    object Repaired : RepairOutcome

    /** User declined the UAC prompt. */
    object Cancelled : RepairOutcome

    /** Firewall fix ran (or wasn't applicable) but the block persists → show product guidance. */
    data class Guidance(val products: List<String>) : RepairOutcome
}

/**
 * Detect-first connectivity repair. [assess] (cheap, unprivileged) decides whether to lead with the
 * firewall fix (Defender / none detected) or with product guidance (a third-party AV like Dr.Web whose
 * own filter a firewall rule can't touch). [tryFirewallFix] elevates once and verifies with a probe —
 * used as the primary action for Defender and as a secondary "try anyway" for third-party AV.
 */
class ConnectivityRepairOrchestrator(
    private val binaries: BinaryResolver,
    private val runner: ElevatedRunner,
    private val probe: ConnectivityProbe,
    private val detector: SecurityProductDetector,
    private val scriptDir: File,
    private val markerWaitMs: Long = 5_000,
) {
    // The installed AV set cannot change mid-session; caching avoids re-spawning the WMI
    // PowerShell query on every retry from the ConnectivityBlocked screen.
    private var cachedDetection: DetectedSecurity? = null

    fun assess(): Assessment {
        val d = cachedDetection ?: detector.detect().also { cachedDetection = it }
        return Assessment(d.products, d.avClass)
    }

    suspend fun tryFirewallFix(): RepairOutcome {
        val bins = binaries.resolve()
        val marker = File(scriptDir, "tp-connectivity-repair.done").also { it.delete() }
        val script = File(scriptDir, "tp-connectivity-repair.cmd")
        // UTF-8 without BOM: the script's own `chcp 65001` makes cmd parse the body as UTF-8.
        script.writeText(FirewallRuleScript.build(bins, marker))
        return when (val r = runner.runElevated(script)) {
            is ElevationResult.UserCancelled -> RepairOutcome.Cancelled
            else -> {
                // Success means "the elevated process finished", not "the rules exist" — and Failed
                // may still have applied them (e.g. the wait timed out mid-run). The marker is the
                // completion signal; give a late script a short grace before probing so the probe
                // doesn't race netsh and report a false "still blocked".
                if (r is ElevationResult.Success) awaitMarker(marker)
                if (probe.probe() == ProbeResult.REACHABLE) {
                    RepairOutcome.Repaired
                } else {
                    RepairOutcome.Guidance(assess().products)
                }
            }
        }
    }

    private suspend fun awaitMarker(marker: File) {
        val deadline = System.currentTimeMillis() + markerWaitMs
        while (!marker.exists() && System.currentTimeMillis() < deadline) {
            delay(100)
        }
        if (!marker.exists() && markerWaitMs > 0) {
            Logger.w("Connectivity", "Repair script completion marker not found; probing anyway")
        }
    }
}
