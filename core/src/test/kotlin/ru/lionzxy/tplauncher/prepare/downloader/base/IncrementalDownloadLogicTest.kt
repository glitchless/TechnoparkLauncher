package ru.lionzxy.tplauncher.prepare.downloader.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
}
