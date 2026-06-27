package ru.lionzxy.tplauncher.prepare.downloader.base

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** Result of interpreting a server changelog against the locally-applied timestamp. */
internal data class ParsedChangeLog(
    /** Merged per-path actions for every changelog bucket newer than the local timestamp. */
    val changes: Map<String, Action>,
    /** Highest changelog timestamp seen (0 when nothing newer than [lastUpdate] exists). */
    val lastTimestamp: Long,
)

private val changeLogGson = Gson()
private val changeLogType = object : TypeToken<Map<String, Map<String, Action>>>() {}.type

/**
 * Resolves [key] (a server-supplied relative path) against [base] and guarantees the result stays
 * inside [base]. Canonicalizing both sides defeats `../` traversal, absolute paths, and Windows
 * drive prefixes regardless of OS. Throws [SecurityException] for anything that escapes.
 */
internal fun resolveContained(base: File, key: String): File {
    val target = File(base, key)
    val basePath = base.canonicalFile.toPath()
    val targetPath = target.canonicalFile.toPath()
    if (!targetPath.startsWith(basePath)) {
        throw SecurityException("Changelog path escapes the modpack directory: '$key'")
    }
    return target
}

/**
 * Parses the changelog (a map of `timestamp -> (path -> action)`), keeps only buckets newer than
 * [lastUpdate], merges them in ascending timestamp order (later buckets win per path), and reports
 * the highest timestamp seen so it can be persisted even when a bucket carries no file operations.
 * A non-numeric timestamp key is skipped rather than aborting the whole changelog.
 */
internal fun parseChangeLog(json: String, lastUpdate: Long): ParsedChangeLog {
    val raw: Map<String, Map<String, Action>> = changeLogGson.fromJson(json, changeLogType) ?: emptyMap()
    val buckets = raw.entries
        .mapNotNull { entry -> entry.key.toLongOrNull()?.let { ts -> ts to entry.value } }
        .filter { (timestamp, _) -> timestamp > lastUpdate }
        .sortedBy { (timestamp, _) -> timestamp }
    val merged = LinkedHashMap<String, Action>()
    buckets.forEach { (_, ops) -> merged.putAll(ops) }
    return ParsedChangeLog(changes = merged, lastTimestamp = buckets.lastOrNull()?.first ?: 0L)
}

/** Joins a host base URL and a relative key with exactly one `/`, regardless of stray slashes. */
internal fun joinUrl(host: String, key: String): String =
    host.removeSuffix("/") + "/" + key.removePrefix("/")

/**
 * Runs [block] over every item in [items] with at most [parallelism] operations IN FLIGHT at once,
 * returning the `(item, error)` pairs that threw (in input order); a failing item never cancels its
 * siblings, and this function itself never throws.
 *
 * The coroutine [Semaphore] is load-bearing: bounding concurrency with a limited dispatcher
 * (`Dispatchers.IO.limitedParallelism(n)`) does NOT cap suspending I/O, because a coroutine awaiting
 * the network releases its dispatcher thread, letting the next of the thousands of already-launched
 * coroutines start. That let an entire modpack's worth of downloads hit the server simultaneously
 * and drown the HTTP connection pool in concurrent TLS handshakes -> widespread ConnectTimeout. The
 * semaphore caps the genuinely-concurrent requests so HTTP keep-alive reuses a small connection pool.
 */
internal suspend fun <T> mapWithBoundedConcurrency(
    items: List<T>,
    parallelism: Int,
    block: suspend (T) -> Unit,
): List<Pair<T, Throwable>> {
    require(parallelism >= 1) { "parallelism must be >= 1, was $parallelism" }
    val semaphore = Semaphore(parallelism)
    return coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching { block(item) }.exceptionOrNull()?.let { item to it }
                }
            }
        }.awaitAll().filterNotNull()
    }
}

/** A validated set of file operations to apply against the modpack directory. */
internal data class DownloadPlan(
    val toDelete: List<File>,
    /** (relativeKey, destinationFile) pairs to fetch. */
    val toDownload: List<Pair<String, File>>,
)

/**
 * Resolves every changelog entry against [base], rejecting the WHOLE update via [SecurityException]
 * if any key (REMOVE or ADD) escapes [base]. Splits the entries into deletions and downloads.
 */
internal fun buildDownloadPlan(base: File, changes: Map<String, Action>): DownloadPlan {
    val toDelete = ArrayList<File>()
    val toDownload = ArrayList<Pair<String, File>>()
    changes.forEach { (key, action) ->
        val target = resolveContained(base, key) // throws -> aborts the whole update
        when (action) {
            Action.REMOVE -> toDelete.add(target)
            Action.ADD -> toDownload.add(key to target)
        }
    }
    return DownloadPlan(toDelete, toDownload)
}
