package ru.lionzxy.tplauncher.log

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** A single structured log record. [format] renders the canonical text line(s). */
data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /**
     * `HH:mm:ss [LEVEL] tag: message`.
     *
     * The `[LEVEL]` token is padded to a fixed 7 columns so tags line up in the
     * monospace log view (`[ERROR]`/`[DEBUG]` are 7 wide; `[INFO]`/`[WARN]` get a
     * trailing space). [message] may contain newlines (e.g. a stack trace) — the
     * record stays a single logical entry on disk (see [LogStore]).
     */
    fun format(): String {
        val time = TIME_FMT.format(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()))
        val levelToken = "[${level.name}]".padEnd(7)
        return "$time $levelToken $tag: $message"
    }
}

/** A record as read back from the log file: its [level] (from the index) + formatted [text]. */
data class LogLine(val level: LogLevel, val text: String)
