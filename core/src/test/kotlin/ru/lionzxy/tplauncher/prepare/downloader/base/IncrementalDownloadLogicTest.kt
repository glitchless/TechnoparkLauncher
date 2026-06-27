package ru.lionzxy.tplauncher.prepare.downloader.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.utils.sha256Hex
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class IncrementalDownloadLogicTest {

    private fun tempBase() = Files.createTempDirectory("incdl").toFile()

    // ---- resolveContained: the security-critical path-traversal guard ----

    @Test
    fun resolveContainedReturnsFileInsideBase() {
        val base = tempBase()
        val resolved = resolveContained(base, "mods/foo.jar")
        assertEquals(base.resolve("mods/foo.jar").canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun resolveContainedAllowsDeeplyNestedSubdirs() {
        val base = tempBase()
        val resolved = resolveContained(base, "a/b/c/d.txt")
        assertTrue(resolved.canonicalPath.startsWith(base.canonicalPath))
    }

    @Test(expected = SecurityException::class)
    fun resolveContainedRejectsParentTraversalEscape() {
        resolveContained(tempBase(), "../../../etc/passwd")
    }

    @Test(expected = SecurityException::class)
    fun resolveContainedRejectsTraversalMixedWithValidSegments() {
        resolveContained(tempBase(), "mods/../../../../tmp/evil")
    }

    @Test
    fun resolveContainedAllowsInternalDotDotThatStaysInsideBase() {
        // "a/../b.txt" canonicalizes to "<base>/b.txt" — contained, so allowed.
        val base = tempBase()
        val resolved = resolveContained(base, "a/../b.txt")
        assertEquals(base.resolve("b.txt").canonicalPath, resolved.canonicalPath)
    }

    // ---- parseChangeLog: filtering, ordering, merge, tolerance ----

    @Test
    fun parseChangeLogMergesBucketsWithLaterTimestampWinning() {
        val json = """{"100": {"a.txt": "1"}, "200": {"b.txt": "1", "a.txt": "0"}}"""
        val parsed = parseChangeLog(json, lastUpdate = 0)
        assertEquals(200L, parsed.lastTimestamp)
        assertEquals(Action.REMOVE, parsed.changes["a.txt"]) // bucket 200 overrides bucket 100
        assertEquals(Action.ADD, parsed.changes["b.txt"])
    }

    @Test
    fun parseChangeLogSkipsAlreadyAppliedTimestamps() {
        val json = """{"100": {"a.txt": "1"}, "200": {"b.txt": "1"}}"""
        val parsed = parseChangeLog(json, lastUpdate = 100)
        assertEquals(200L, parsed.lastTimestamp)
        assertNull(parsed.changes["a.txt"]) // bucket 100 already applied
        assertEquals(Action.ADD, parsed.changes["b.txt"])
    }

    @Test
    fun parseChangeLogToleratesNonNumericTimestampKey() {
        // A single malformed key must not discard the whole changelog.
        val json = """{"bogus": {"x.txt": "1"}, "300": {"y.txt": "1"}}"""
        val parsed = parseChangeLog(json, lastUpdate = 0)
        assertEquals(300L, parsed.lastTimestamp)
        assertNull(parsed.changes["x.txt"])
        assertEquals(Action.ADD, parsed.changes["y.txt"])
    }

    @Test
    fun parseChangeLogReportsTimestampEvenWhenBucketHasNoFileOps() {
        // An empty bucket still advances the timestamp so it isn't reprocessed forever.
        val parsed = parseChangeLog("""{"500": {}}""", lastUpdate = 0)
        assertEquals(500L, parsed.lastTimestamp)
        assertTrue(parsed.changes.isEmpty())
    }

    @Test
    fun parseChangeLogReturnsZeroTimestampWhenNothingIsNewer() {
        val parsed = parseChangeLog("""{"100": {"a.txt": "1"}}""", lastUpdate = 100)
        assertEquals(0L, parsed.lastTimestamp)
        assertTrue(parsed.changes.isEmpty())
    }

    // ---- buildDownloadPlan: splits ops and aborts the whole update on any traversal ----

    @Test
    fun buildDownloadPlanSplitsRemovesAndAdds() {
        val base = tempBase()
        val changes = linkedMapOf("old.txt" to Action.REMOVE, "mods/new.jar" to Action.ADD)
        val plan = buildDownloadPlan(base, changes)
        assertEquals(listOf(base.resolve("old.txt").canonicalPath), plan.toDelete.map { it.canonicalPath })
        assertEquals(1, plan.toDownload.size)
        assertEquals("mods/new.jar", plan.toDownload[0].first)
        assertEquals(base.resolve("mods/new.jar").canonicalPath, plan.toDownload[0].second.canonicalPath)
    }

    @Test(expected = SecurityException::class)
    fun buildDownloadPlanAbortsOnTraversalInAdd() {
        buildDownloadPlan(tempBase(), mapOf("../evil.sh" to Action.ADD))
    }

    @Test(expected = SecurityException::class)
    fun buildDownloadPlanAbortsOnTraversalInRemove() {
        buildDownloadPlan(tempBase(), mapOf("../../secret.cfg" to Action.REMOVE))
    }

    // ---- joinUrl: single-slash normalization regardless of host trailing slash ----

    @Test
    fun joinUrlInsertsSlashWhenHostHasNoTrailingSlash() {
        assertEquals(
            "https://h/incremental/vanilla/mods/a.jar",
            joinUrl("https://h/incremental/vanilla", "mods/a.jar"),
        )
    }

    @Test
    fun joinUrlCollapsesWhenBothSidesHaveSlash() {
        assertEquals(
            "https://h/incremental/as/mods/a.jar",
            joinUrl("https://h/incremental/as/", "/mods/a.jar"),
        )
    }

    // ---- mapWithBoundedConcurrency: a REAL in-flight cap for suspending I/O ----

    @Test
    fun mapWithBoundedConcurrencyNeverExceedsTheLimitAcrossSuspension() = runBlocking {
        // Reproduces the modpack-download bug: a dispatcher's limitedParallelism caps THREADS, not
        // in-flight suspending operations, so suspending work (network I/O) sails past it and every
        // request hits the server at once. The semaphore-based helper must hold the true concurrent
        // count at or below the limit even though each unit suspends.
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val failures = mapWithBoundedConcurrency((1..500).toList(), parallelism = 8) {
            val now = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            delay(5) // suspends, freeing the thread — exactly where limitedParallelism fails to bound
            inFlight.decrementAndGet()
            Unit
        }
        assertTrue(failures.isEmpty())
        assertTrue("max in-flight ${maxInFlight.get()} exceeded the limit of 8", maxInFlight.get() <= 8)
    }

    @Test
    fun mapWithBoundedConcurrencyCollectsFailuresAndStillRunsTheRest() = runBlocking {
        val completed = AtomicInteger(0)
        val failures = mapWithBoundedConcurrency((1..20).toList(), parallelism = 4) { n ->
            if (n % 5 == 0) throw RuntimeException("boom $n") else completed.incrementAndGet()
            Unit
        }
        // Every non-failing item ran — a single failure must not cancel its siblings…
        assertEquals(16, completed.get())
        // …and exactly the failing items are reported, each carrying its own error.
        assertEquals(setOf(5, 10, 15, 20), failures.map { it.first }.toSet())
        assertTrue(failures.all { it.second is RuntimeException })
    }

    // ---- changelog "sha256" optional map ----

    @Test
    fun parseChangeLogReadsOptionalSha256Map() {
        val json = """
            {"100": {"a.txt": "1", "old.txt": "0"},
             "sha256": {"a.txt": "deadbeef"}}
        """.trimIndent()
        val parsed = parseChangeLog(json, lastUpdate = 0)
        assertEquals(100L, parsed.lastTimestamp)
        assertEquals(Action.ADD, parsed.changes["a.txt"])
        assertEquals(Action.REMOVE, parsed.changes["old.txt"])
        assertEquals("deadbeef", parsed.hashes["a.txt"])
        // the "sha256" key is NOT treated as a timestamp bucket
        assertNull(parsed.changes["sha256"])
    }

    @Test
    fun parseChangeLogWithNoSha256MapYieldsEmptyHashes() {
        val parsed = parseChangeLog("""{"100": {"a.txt": "1"}}""", lastUpdate = 0)
        assertEquals(Action.ADD, parsed.changes["a.txt"])
        assertTrue(parsed.hashes.isEmpty())
    }

    @Test
    fun parseChangeLogToleratesIntActionValues() {
        val parsed = parseChangeLog("""{"100": {"a.txt": 1, "b.txt": 0}}""", lastUpdate = 0)
        assertEquals(Action.ADD, parsed.changes["a.txt"])
        assertEquals(Action.REMOVE, parsed.changes["b.txt"])
    }

    // ---- filterUpToDate: hash-skip ----

    private fun writeFile(dir: File, name: String, content: String): File {
        val f = File(dir, name); f.parentFile.mkdirs(); f.writeText(content); return f
    }

    @Test
    fun filterUpToDateSkipsMatching_keepsMismatchedMissingAndUnhashed() = runBlocking {
        val dir = tempBase()
        val match = writeFile(dir, "match.txt", "same")
        val mism = writeFile(dir, "mismatch.txt", "local-different")
        val expected = mapOf(
            "match.txt" to match.sha256Hex(),          // present + matches  -> skip
            "mismatch.txt" to "0000",                  // present + differs  -> keep
            "missing.txt" to "abcd",                   // absent             -> keep
            // "nohash.txt" has no expected hash        // unhashed           -> keep
        )
        val toDownload = listOf(
            "match.txt" to File(dir, "match.txt"),
            "mismatch.txt" to File(dir, "mismatch.txt"),
            "missing.txt" to File(dir, "missing.txt"),
            "nohash.txt" to File(dir, "nohash.txt"),
        )
        val remaining = filterUpToDate(toDownload, expected, parallelism = 4)
        assertEquals(
            setOf("mismatch.txt", "missing.txt", "nohash.txt"),
            remaining.map { it.first }.toSet(),
        )
    }
}
