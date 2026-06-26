package ru.lionzxy.tplauncher.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogEntryTest {

    @Test
    fun format_isTimeLevelTagMessage() {
        val entry = LogEntry(timeMillis = 0L, level = LogLevel.INFO, tag = "Downloader", message = "hello")
        val formatted = entry.format()
        // HH:mm:ss [LEVEL] tag: message  (level token padded so tags align)
        assertTrue(formatted, Regex("""^\d{2}:\d{2}:\d{2} \[INFO]\s+Downloader: hello$""").matches(formatted))
    }

    @Test
    fun format_rendersEachLevelToken() {
        for (level in LogLevel.values()) {
            val formatted = LogEntry(0L, level, "T", "m").format()
            assertTrue(formatted, formatted.contains("[${level.name}]"))
        }
    }

    @Test
    fun format_keepsMultiLineMessageIntact() {
        val entry = LogEntry(0L, LogLevel.ERROR, "Auth", "boom\n\tat Foo.bar(Foo.kt:1)")
        val formatted = entry.format()
        assertTrue(formatted, formatted.contains("boom"))
        assertTrue(formatted, formatted.contains("\tat Foo.bar(Foo.kt:1)"))
    }
}

class LogStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newStore(captured: MutableList<LogEntry>? = null): LogStore {
        val file = tmp.newFile("session.log")
        return if (captured == null) LogStore(file) else LogStore(file) { captured.add(it) }
    }

    @Test
    fun count_startsAtZero() {
        assertEquals(0, newStore().count.value)
    }

    @Test
    fun append_incrementsCount() {
        val store = newStore()
        store.log(LogLevel.INFO, "T", "one")
        store.log(LogLevel.INFO, "T", "two")
        store.log(LogLevel.INFO, "T", "three")
        assertEquals(3, store.count.value)
    }

    @Test
    fun read_returnsRequestedWindowWithLevelAndFormattedText() {
        val store = newStore()
        val a = LogEntry(0L, LogLevel.INFO, "T", "alpha")
        val b = LogEntry(0L, LogLevel.WARN, "T", "bravo")
        val c = LogEntry(0L, LogLevel.ERROR, "T", "charlie")
        store.log(a); store.log(b); store.log(c)

        val window = store.read(1, 2)
        assertEquals(2, window.size)
        assertEquals(LogLevel.WARN, window[0].level)
        assertEquals(b.format(), window[0].text)
        assertEquals(LogLevel.ERROR, window[1].level)
        assertEquals(c.format(), window[1].text)
    }

    @Test
    fun read_clampsAndRejectsOutOfRange() {
        val store = newStore()
        repeat(3) { store.log(LogLevel.INFO, "T", "m$it") }
        assertEquals(2, store.read(1, 10).size)   // clamps to what exists
        assertEquals(0, store.read(5, 10).size)    // beyond end -> empty
        assertEquals(0, store.read(0, 0).size)     // zero count -> empty
        assertEquals(0, store.read(-1, 5).size)    // negative from -> empty
    }

    @Test
    fun read_keepsMultiLineRecordAsSingleEntry() {
        val store = newStore()
        store.log(LogLevel.ERROR, "Auth", "failed", RuntimeException("kaboom"))
        assertEquals("multi-line stack trace is one record", 1, store.count.value)

        val lines = store.read(0, 1)
        assertEquals(1, lines.size)
        assertEquals(LogLevel.ERROR, lines[0].level)
        assertTrue(lines[0].text, lines[0].text.contains("failed"))
        assertTrue(lines[0].text, lines[0].text.contains("kaboom"))
        assertTrue(lines[0].text, lines[0].text.contains("RuntimeException"))
    }

    @Test
    fun readAll_returnsWholeFileContents() {
        val store = newStore()
        store.log(LogLevel.INFO, "T", "first")
        store.log(LogLevel.WARN, "T", "second")
        val all = store.readAll()
        assertTrue(all, all.contains("first"))
        assertTrue(all, all.contains("second"))
        // readAll matches what's on disk
        assertEquals(store.logFile.readText(), all)
    }

    @Test
    fun mirror_receivesEveryEntry() {
        val captured = mutableListOf<LogEntry>()
        val store = newStore(captured)
        store.log(LogLevel.INFO, "T", "x")
        store.log(LogLevel.ERROR, "T", "y")
        assertEquals(2, captured.size)
        assertEquals("x", captured[0].message)
        assertEquals(LogLevel.ERROR, captured[1].level)
    }

    @Test
    fun construction_startsFreshFile() {
        val file = tmp.newFile("dirty.log")
        file.writeText("leftover from a previous run\n")
        val store = LogStore(file)
        assertEquals(0, store.count.value)
        assertEquals("", store.readAll())
    }

    @Test
    fun concurrentAppends_areAllRecorded() {
        val store = newStore()
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(100) { store.log(LogLevel.INFO, "T$t", "msg-$t-$it") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(800, store.count.value)
        // every record is readable and intact
        val all = store.read(0, 800)
        assertEquals(800, all.size)
        assertNotNull(all.firstOrNull { it.text.contains("msg-0-0") })
    }

    // ── readAll() guards against pathologically large files (copy-all) ──

    @Test
    fun readAll_underCap_returnsWholeFile() {
        val store = newStore()
        repeat(5) { store.log(LogLevel.INFO, "T", "small-$it") }
        val all = store.readAll() // default cap, tiny file
        assertTrue(all, all.contains("small-0"))
        assertTrue(all, all.contains("small-4"))
        assertEquals(store.logFile.readText(), all)
    }

    @Test
    fun readAll_overCap_returnsTailWithTruncationMarker() {
        val store = newStore()
        repeat(50) { store.log(LogLevel.INFO, "T", "line-$it-padding") }
        val capped = store.readAll(maxBytes = 100) // force truncation to the tail
        assertTrue("marker present: $capped", capped.contains("обрезан"))
        assertTrue("keeps newest", capped.contains("line-49-"))
        assertFalse("drops oldest", capped.contains("line-0-"))
    }

    // ── peekRecent(): synchronous in-memory access to the tail (no file IO) ──

    @Test
    fun peekRecent_matchesReadForRecentRecords() {
        val store = newStore()
        repeat(10) { store.log(LogLevel.values()[it % 4], "T", "rec-$it") }
        for (i in 0 until 10) {
            val peeked = store.peekRecent(i)
            val read = store.read(i, 1).first()
            assertNotNull("peekRecent($i)", peeked)
            assertEquals(read.text, peeked!!.text)
            assertEquals(read.level, peeked.level)
        }
    }

    @Test
    fun peekRecent_returnsNullOutsideRange() {
        val store = newStore()
        repeat(3) { store.log(LogLevel.INFO, "T", "x$it") }
        assertNull(store.peekRecent(-1))
        assertNull(store.peekRecent(3))
        assertNull(store.peekRecent(100))
    }

    @Test
    fun peekRecent_evictsOldFromMemoryButFileRetainsThem() {
        val store = newStore()
        val n = 700 // > the in-memory recent cap
        repeat(n) { store.log(LogLevel.INFO, "T", "m$it") }
        // oldest record has scrolled out of the in-memory tail...
        assertNull("old record not in memory window", store.peekRecent(0))
        // ...but is still readable from the file
        assertEquals("m0", store.read(0, 1).first().text.substringAfterLast(": "))
        // newest record is served from memory
        assertNotNull(store.peekRecent(n - 1))
        assertEquals("m${n - 1}", store.peekRecent(n - 1)!!.text.substringAfterLast(": "))
    }
}
