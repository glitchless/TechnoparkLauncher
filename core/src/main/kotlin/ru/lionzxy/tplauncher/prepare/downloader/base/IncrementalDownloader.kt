package ru.lionzxy.tplauncher.prepare.downloader.base

import com.google.gson.annotations.SerializedName
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.IDownloader
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.UriEncodeUtils
import ru.lionzxy.tplauncher.utils.sha256Hex
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TAG = "Downloader"

abstract class IncrementalDownloader : IDownloader {
    private var changes: Map<String, Action> = emptyMap()
    private var changeHashes: Map<String, String> = emptyMap()
    private var lastChangeTimestamp = 0L
    private val progressMutex = Mutex()

    /**
     * Fetches and parses the changelog. A failure here is best-effort: it is surfaced loudly (status
     * message + log + Sentry) but does NOT abort the launch — the game starts on the currently
     * installed files rather than silently pretending the update succeeded.
     */
    override fun init(minecraft: MinecraftContext) {
        val info = getDownloaderInfo(minecraft)
        val url = info.updateJsonLink
        if (url.isNullOrEmpty()) {
            Logger.i(LOG_TAG, "No update URL for '${info.key}', skipping update list")
            return
        }
        val lastUpdate = ConfigHelper.config.modpackDownloadedInfo[info.key]?.lastUpdateFromChangeLog ?: 0
        minecraft.progressMonitor.setStatus("Получение списка обновлений с сервера...")
        Logger.i(LOG_TAG, "Fetching update list for '${info.key}' from $url")
        try {
            val json = runBlocking { HttpDownloader.instance.getString(url) }
            val parsed = parseChangeLog(json, lastUpdate)
            changes = parsed.changes
            lastChangeTimestamp = parsed.lastTimestamp
            changeHashes = parsed.hashes
            Logger.i(LOG_TAG, "Update list parsed: ${changes.size} changed file(s) since timestamp $lastUpdate")
            minecraft.progressMonitor.setStatus("Данные обновления получены, применяем их...")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to fetch update list for '${info.key}'", e)
            Sentry.captureException(e)
            minecraft.progressMonitor.setStatus("Не удалось получить обновления, запуск на текущей версии")
        }
    }

    override fun download(minecraft: MinecraftContext) {
        val info = getDownloaderInfo(minecraft)
        val base = info.modpackDirectory ?: minecraft.getDirectory()

        if (changes.isEmpty()) {
            Logger.i(LOG_TAG, "No changes to apply for '${info.key}'")
            // A newer (possibly empty) bucket may have advanced the timestamp even with no file ops.
            persistTimestamp(info)
            return
        }

        minecraft.progressMonitor.setStatus("Начинаем загружать обновления...")
        val host = info.updateHostLink
        if (host.isNullOrEmpty()) {
            throw IllegalStateException(
                "updateHostLink is missing for '${info.key}' but the changelog has ${changes.size} change(s)"
            )
        }

        // Validates every key (REMOVE and ADD) against `base`; throws on any path-traversal attempt.
        val plan = buildDownloadPlan(base, changes)

        if (plan.toDelete.isNotEmpty()) {
            minecraft.progressMonitor.setStatus("Удаляем ненужные файлы...")
            Logger.i(LOG_TAG, "Removing ${plan.toDelete.size} obsolete file(s) for '${info.key}'")
            plan.toDelete.forEach { file ->
                Logger.d(LOG_TAG, "Deleting $file")
                if (file.exists() && !file.deleteRecursively()) {
                    Logger.w(LOG_TAG, "Failed to delete $file")
                }
            }
        }

        val toDownload = plan.toDownload
        if (toDownload.isEmpty()) {
            persistTimestamp(info)
            return
        }

        val parallelism = ConfigHelper.config.settings.parallelDownloads

        // Resume: drop files already correct on disk (verified by the server's SHA-256). Files with
        // no expected hash are kept and downloaded as before (base files are validated at install).
        minecraft.progressMonitor.setStatus("Проверка файлов...")
        val pending = runBlocking { filterUpToDate(toDownload, changeHashes, parallelism) }
        val skipped = toDownload.size - pending.size
        if (skipped > 0) {
            Logger.i(LOG_TAG, "Skipped $skipped already-current file(s) for '${info.key}'")
        }
        if (pending.isEmpty()) {
            persistTimestamp(info)
            return
        }

        Logger.i(LOG_TAG, "Downloading ${pending.size} file(s) for '${info.key}'")
        minecraft.progressMonitor.setStatus("Загружаем модпак...")
        minecraft.progressMonitor.setMax(pending.size)
        minecraft.progressMonitor.setProgress(0)

        val downloadedFiles = AtomicInteger(0)
        val failures = runBlocking {
            downloadWithRetries(
                items = pending,
                parallelism = parallelism,
                maxAttempts = DOWNLOAD_ATTEMPTS,
            ) { (k, file) ->
                Logger.d(LOG_TAG, "Downloading $k")
                val url = UriEncodeUtils.encodePath(joinUrl(host, k), Charsets.UTF_8)
                HttpDownloader.instance.downloadToFile(url, file)
                changeHashes[k]?.let { expected ->
                    val actual = file.sha256Hex()
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw IOException("hash mismatch for $k: expected $expected got $actual")
                    }
                }
                val done = downloadedFiles.incrementAndGet()
                progressMutex.withLock {
                    minecraft.progressMonitor.setStatus("Загружено $done/${pending.size}")
                    minecraft.progressMonitor.setProgress(done)
                }
            }.map { (item, error) -> item.first to error }
        }

        Logger.i(LOG_TAG, "Downloaded ${downloadedFiles.get()}/${pending.size} file(s) for '${info.key}'")

        if (failures.isNotEmpty()) {
            Logger.e(LOG_TAG, "${failures.size} file(s) failed to download for '${info.key}'")
            failures.forEach { (key, error) ->
                Logger.e(LOG_TAG, "Download failed: $key", error)
            }
            val primary = failures.first().second
            val aggregated = IOException(
                "Failed to download ${failures.size}/${pending.size} file(s): " +
                    failures.joinToString(", ") { it.first },
                primary,
            )
            failures.drop(1).forEach { aggregated.addSuppressed(it.second) }
            throw aggregated
        }

        persistTimestamp(info)
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true

    private fun persistTimestamp(info: IncrementalDownloaderInfo) {
        if (lastChangeTimestamp <= 0) {
            return
        }
        ConfigHelper.writeToConfig {
            val downloadedInfo = modpackDownloadedInfo[info.key] ?: DownloadedInfo()
            downloadedInfo.lastUpdateFromChangeLog = lastChangeTimestamp
            modpackDownloadedInfo[info.key] = downloadedInfo
        }
    }

    abstract fun getDownloaderInfo(minecraft: MinecraftContext): IncrementalDownloaderInfo

    private companion object {
        // 1 initial pass + 2 retries of the still-failing subset (hash-skip keeps retries cheap).
        const val DOWNLOAD_ATTEMPTS = 3
    }
}

data class IncrementalDownloaderInfo(
    val key: String,
    // Json file with meta information about update
    val updateJsonLink: String?,
    // Where download files
    val updateHostLink: String?,
    // Where save file
    val modpackDirectory: File? = null
)

enum class Action {
    @SerializedName("0")
    REMOVE,

    @SerializedName("1")
    ADD
}
