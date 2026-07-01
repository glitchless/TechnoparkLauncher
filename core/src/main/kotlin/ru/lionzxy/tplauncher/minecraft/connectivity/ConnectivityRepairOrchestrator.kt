package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/** What the UI should lead with, decided by the cheap unprivileged [ConnectivityRepairOrchestrator.assess]. */
data class Assessment(val products: List<String>, val firewallFixFirst: Boolean, val avClass: AvClass)

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
) {
    private var lastDetected: DetectedSecurity = DetectedSecurity(emptyList(), AvClass.NONE_DETECTED)

    fun assess(): Assessment {
        val d = detector.detect().also { lastDetected = it }
        val firewallFirst = d.avClass != AvClass.THIRD_PARTY_NETWORK
        return Assessment(d.products, firewallFirst, d.avClass)
    }

    suspend fun tryFirewallFix(): RepairOutcome {
        val bins = binaries.resolve()
        val marker = File(scriptDir, "tp-connectivity-repair.done").also { it.delete() }
        val script = File(scriptDir, "tp-connectivity-repair.cmd")
        script.writeText(FirewallRuleScript.build(bins, marker))
        return when (runner.runElevated(script)) {
            is ElevationResult.UserCancelled -> RepairOutcome.Cancelled
            else -> if (probe.probe() == ProbeResult.REACHABLE) {
                RepairOutcome.Repaired
            } else {
                RepairOutcome.Guidance(lastDetected.products)
            }
        }
    }
}
