package ru.lionzxy.tplauncher.minecraft.connectivity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeDetector(val d: DetectedSecurity) : SecurityProductDetector {
    var calls = 0
    override fun detect(): DetectedSecurity {
        calls++
        return d
    }
}

private class FakeRunner(val r: ElevationResult) : ElevatedRunner {
    var called = false
    override fun runElevated(scriptFile: File): ElevationResult {
        called = true
        return r
    }
}

private class FakeProbe(val r: ProbeResult) : ConnectivityProbe {
    override suspend fun probe() = r
}

private class FakeBins(val list: List<File>) : BinaryResolver {
    override fun resolve() = list
}

class ConnectivityRepairOrchestratorTest {
    private fun orch(det: SecurityProductDetector, runner: FakeRunner, probe: ProbeResult) =
        ConnectivityRepairOrchestrator(
            FakeBins(listOf(File("/a/javaw.exe"))),
            runner,
            FakeProbe(probe),
            det,
            File(System.getProperty("java.io.tmpdir")),
            markerWaitMs = 0, // fakes never write the marker; don't stall unit tests
        )

    private fun orch(det: DetectedSecurity, runner: FakeRunner, probe: ProbeResult) =
        orch(FakeDetector(det), runner, probe)

    @Test
    fun thirdPartyAvDoesNotLeadWithFirewall() {
        val a = orch(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK), FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED).assess()
        assertFalse(a.firewallFixFirst)
        assertEquals(listOf("Dr.Web"), a.products)
        assertEquals(AvClass.THIRD_PARTY_NETWORK, a.avClass)
    }

    @Test
    fun defenderLeadsWithFirewall() {
        val a = orch(DetectedSecurity(listOf("Windows Defender"), AvClass.DEFENDER_ONLY), FakeRunner(ElevationResult.Success), ProbeResult.REACHABLE).assess()
        assertTrue(a.firewallFixFirst)
    }

    @Test
    fun noneDetectedLeadsWithFirewall() {
        val a = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), FakeRunner(ElevationResult.Success), ProbeResult.REACHABLE).assess()
        assertTrue(a.firewallFixFirst)
    }

    @Test
    fun assessCachesDetectionAcrossRetries() {
        // Every retry from the blocked screen re-enters assess(); the AV set can't change
        // mid-session, so the (PowerShell-spawning) detector must run only once.
        val det = FakeDetector(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK))
        val o = orch(det, FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED)
        o.assess()
        o.assess()
        o.assess()
        assertEquals(1, det.calls)
    }

    @Test
    fun firewallFixReachableIsRepaired() = runBlocking {
        val r = FakeRunner(ElevationResult.Success)
        val out = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), r, ProbeResult.REACHABLE).tryFirewallFix()
        assertTrue(r.called)
        assertEquals(RepairOutcome.Repaired, out)
    }

    @Test
    fun firewallCancelledIsCancelled() = runBlocking {
        val out = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), FakeRunner(ElevationResult.UserCancelled), ProbeResult.BLOCKED).tryFirewallFix()
        assertEquals(RepairOutcome.Cancelled, out)
    }

    @Test
    fun firewallRanButStillBlockedGivesGuidanceWithProducts() = runBlocking {
        val o = orch(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK), FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED)
        o.assess() // populate detected products (as the UI does on entering the screen)
        val out = o.tryFirewallFix()
        assertTrue(out is RepairOutcome.Guidance)
        assertEquals(listOf("Dr.Web"), (out as RepairOutcome.Guidance).products)
    }

    @Test
    fun elevationFailureStillProbesAndCanRepair() = runBlocking {
        // A wait timeout reports Failed, but the rules may have been applied moments later —
        // the probe stays the authority on the outcome.
        val out = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), FakeRunner(ElevationResult.Failed("timeout")), ProbeResult.REACHABLE).tryFirewallFix()
        assertEquals(RepairOutcome.Repaired, out)
    }

    @Test
    fun guidanceWorksEvenWithoutPriorAssess() = runBlocking {
        // tryFirewallFix invoked directly (no assess() first) must still report detected products.
        val o = orch(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK), FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED)
        val out = o.tryFirewallFix()
        assertEquals(listOf("Dr.Web"), (out as RepairOutcome.Guidance).products)
    }
}
