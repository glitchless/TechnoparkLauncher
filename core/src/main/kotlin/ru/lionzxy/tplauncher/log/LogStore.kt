package ru.lionzxy.tplauncher.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/**
 * File-backed, thread-safe log store. Each [log] call appends one **record** (the
 * formatted text, which may span multiple lines for a stack trace) to [logFile],
 * and keeps a tiny in-memory index of `(byteOffset, level)` per record so the UI
 * can read any window back from disk via [read] without holding entries in memory.
 *
 * Records are delimited by their byte offsets (not by newlines), so a multi-line
 * record (a throwable's stack trace) stays a single logical entry.
 *
 * A single [RandomAccessFile] in `"rw"` mode is used for both appends and
 * positioned reads under one [lock]; `"rw"` writes go straight to the OS so reads
 * always see committed bytes. Appends are cheap (one positioned write), safe to
 * call from the 64-thread download pool.
 */
class LogStore(
    val logFile: File,
    private val mirror: (LogEntry) -> Unit = ::printToConsole,
) {
    private val lock = Any()
    private val raf: RandomAccessFile
    private val offsets = ArrayList<Long>()
    private val levels = ArrayList<LogLevel>()
    // Bounded in-memory mirror of the most recent records, so the UI can render the live
    // tail synchronously (no async file read) instead of flashing a blank newest line.
    private val recent = ArrayDeque<LogLine>()

    private val _count = MutableStateFlow(0)

    /** Number of records written. Drives the UI's lazy list size and autoscroll. */
    val count: StateFlow<Int> = _count.asStateFlow()

    init {
        logFile.parentFile?.mkdirs()
        raf = RandomAccessFile(logFile, "rw")
        raf.setLength(0) // per-launch file: always start fresh
    }

    fun log(entry: LogEntry) {
        val text = entry.format()
        val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            val offset = raf.length()
            raf.seek(offset)
            raf.write(bytes)
            offsets.add(offset)
            levels.add(entry.level)
            recent.addLast(LogLine(entry.level, text))
            if (recent.size > RECENT_CAP) recent.removeFirst()
            _count.value = offsets.size
        }
        mirror(entry)
    }

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) =
        log(LogEntry(System.currentTimeMillis(), level, tag, buildMessage(message, throwable)))

    /** Read records `[from, from + count)`, clamped to what exists. Off-UI-thread callers only. */
    fun read(from: Int, count: Int): List<LogLine> {
        if (from < 0 || count <= 0) return emptyList()
        synchronized(lock) {
            val size = offsets.size
            if (from >= size) return emptyList()
            val end = minOf(from + count, size)
            val result = ArrayList<LogLine>(end - from)
            for (i in from until end) {
                val start = offsets[i]
                val stop = if (i + 1 < size) offsets[i + 1] else raf.length()
                val buf = ByteArray((stop - start).toInt())
                raf.seek(start)
                raf.readFully(buf)
                result.add(LogLine(levels[i], String(buf, Charsets.UTF_8).removeSuffix("\n")))
            }
            return result
        }
    }

    /**
     * Whole-file contents for copy-all, bounded to [maxBytes] to avoid an Int-overflow on the
     * buffer size and an OutOfMemory on a pathologically large log. When the file is larger,
     * only the most recent [maxBytes] bytes are returned, prefixed with a truncation marker
     * (the full log is always available via Save, which streams the file).
     */
    fun readAll(maxBytes: Long = MAX_COPY_BYTES): String = synchronized(lock) {
        val len = raf.length()
        if (len <= maxBytes) {
            val buf = ByteArray(len.toInt())
            raf.seek(0)
            raf.readFully(buf)
            String(buf, Charsets.UTF_8)
        } else {
            val buf = ByteArray(maxBytes.toInt())
            raf.seek(len - maxBytes)
            raf.readFully(buf)
            LOG_TRUNCATED_MARKER + String(buf, Charsets.UTF_8)
        }
    }

    /**
     * The record at global [index] if it is still in the in-memory tail window, else null.
     * Synchronous (no file IO) — safe to call during composition to render the live tail
     * without the one-frame blank that an async page read would otherwise cause.
     */
    fun peekRecent(index: Int): LogLine? = synchronized(lock) {
        val recentStart = offsets.size - recent.size
        if (index in recentStart until offsets.size) recent[index - recentStart] else null
    }
}

private const val MAX_COPY_BYTES = 32L * 1024 * 1024

// How many of the most recent records to keep mirrored in memory for synchronous tail reads.
private const val RECENT_CAP = 512

private const val LOG_TRUNCATED_MARKER = "… (лог обрезан; используйте «Сохранить» для полного файла) …\n"

private fun buildMessage(message: String, throwable: Throwable?): String =
    if (throwable == null) message else "$message\n${throwable.stackTraceToString().trimEnd()}"

/** Default mirror: stdout for DEBUG/INFO, stderr for WARN/ERROR. */
private fun printToConsole(entry: LogEntry) {
    val line = entry.format()
    when (entry.level) {
        LogLevel.WARN, LogLevel.ERROR -> System.err.println(line)
        else -> println(line)
    }
}
