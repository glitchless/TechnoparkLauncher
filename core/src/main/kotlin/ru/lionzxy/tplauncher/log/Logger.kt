package ru.lionzxy.tplauncher.log

import kotlinx.coroutines.flow.StateFlow
import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Process-wide logging entry point. On first use it lazily opens a **per-launch**
 * log file at `<config>/logs/launcher-<timestamp>.log` and mirrors every entry to
 * the console (stdout for DEBUG/INFO, stderr for WARN/ERROR).
 *
 * File logging is always on; the in-window log panel is a separate opt-in
 * (`Settings.enableLogView`). All real work lives in the tested [LogStore]; this
 * object is just the session-scoped composition root + ergonomic `d/i/w/e` API.
 */
object Logger {
    private val store: LogStore by lazy { LogStore(resolveLogFile()) }

    /** Number of records written so far. Drives the UI lazy list size and autoscroll. */
    val count: StateFlow<Int> get() = store.count

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        store.log(LogLevel.DEBUG, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        store.log(LogLevel.INFO, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        store.log(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        store.log(LogLevel.ERROR, tag, message, throwable)

    /** Read records `[from, from + count)` from the session file. Call off the UI thread (does file IO). */
    fun read(from: Int, count: Int): List<LogLine> = store.read(from, count)

    /** The entire session log as text (for copy-all), bounded to avoid OOM on huge logs. */
    fun readAll(): String = store.readAll()

    /**
     * The record at global [index] if it is still in the in-memory tail window, else null.
     * Synchronous — lets the UI render the freshest lines without an async file read.
     */
    fun peekRecent(index: Int): LogLine? = store.peekRecent(index)

    /** The current session log file (for Save As). */
    fun logFile(): File = store.logFile

    private fun resolveLogFile(): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return File(ConfigHelper.getLogsDirectory(), "launcher-$stamp.log")
    }
}
