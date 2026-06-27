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
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TAG = "Downloader"

abstract class IncrementalDownloader : IDownloader {
    private var changes: Map<String, Action> = emptyMap()
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

        Logger.i(LOG_TAG, "Downloading ${toDownload.size} file(s) for '${info.key}'")
        minecraft.progressMonitor.setStatus("Загружаем модпак...")
        minecraft.progressMonitor.setMax(toDownload.size)
        minecraft.progressMonitor.setProgress(0)

        val downloadedFiles = AtomicInteger(0)
        val failures = runBlocking {
            // Bound the truly-concurrent downloads (see mapWithBoundedConcurrency): a limited
            // dispatcher does NOT cap suspending network I/O, so the old code fired every file at
            // the host at once and the connection storm produced widespread connect timeouts.
            mapWithBoundedConcurrency(toDownload, DOWNLOAD_PARALLELISM) { (key, file) ->
                Logger.d(LOG_TAG, "Downloading $key")
                val url = UriEncodeUtils.encodePath(joinUrl(host, key), Charsets.UTF_8)
                HttpDownloader.instance.downloadToFile(url, file)
                val done = downloadedFiles.incrementAndGet()
                progressMutex.withLock {
                    minecraft.progressMonitor.setStatus("Загружено $done/${toDownload.size}")
                    minecraft.progressMonitor.setProgress(done)
                }
            }.map { (item, error) -> item.first to error }
        }

        Logger.i(LOG_TAG, "Downloaded ${downloadedFiles.get()}/${toDownload.size} file(s) for '${info.key}'")

        if (failures.isNotEmpty()) {
            Logger.e(LOG_TAG, "${failures.size} file(s) failed to download for '${info.key}'")
            failures.forEach { (key, error) ->
                Logger.e(LOG_TAG, "Download failed: $key", error)
            }
            val primary = failures.first().second
            val aggregated = IOException(
                "Failed to download ${failures.size}/${toDownload.size} file(s): " +
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
        // Keep concurrent connections modest: a burst of dozens of simultaneous TLS handshakes to
        // the Cloudflare-fronted host caused widespread connect timeouts. With HTTP keep-alive the
        // client reuses this small pool of connections across all files, so throughput stays high.
        const val DOWNLOAD_PARALLELISM = 8
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
