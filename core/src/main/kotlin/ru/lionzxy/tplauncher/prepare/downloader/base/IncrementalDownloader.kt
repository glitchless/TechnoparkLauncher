package ru.lionzxy.tplauncher.prepare.downloader.base

import com.google.gson.annotations.SerializedName
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.IDownloader
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.UriEncodeUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

abstract class IncrementalDownloader : IDownloader {
    private var changes: Map<String, Action> = emptyMap()
    private var lastChangeTimestamp = 0L
    private val progressMutex = Mutex()

    /**
     * Fetches and parses the changelog. A failure here is best-effort: it is surfaced loudly (status
     * message + Sentry + stderr) but does NOT abort the launch — the game starts on the currently
     * installed files rather than silently pretending the update succeeded.
     */
    override fun init(minecraft: MinecraftContext) {
        val info = getDownloaderInfo(minecraft)
        val url = info.updateJsonLink
        if (url.isNullOrEmpty()) {
            return
        }
        val lastUpdate = ConfigHelper.config.modpackDownloadedInfo[info.key]?.lastUpdateFromChangeLog ?: 0
        minecraft.progressMonitor.setStatus("Получение списка обновлений с сервера...")
        try {
            val json = runBlocking { HttpDownloader.instance.getString(url) }
            val parsed = parseChangeLog(json, lastUpdate)
            changes = parsed.changes
            lastChangeTimestamp = parsed.lastTimestamp
            minecraft.progressMonitor.setStatus("Данные обновления получены, применяем их...")
        } catch (e: Exception) {
            e.printStackTrace()
            Sentry.captureException(e)
            minecraft.progressMonitor.setStatus("Не удалось получить обновления, запуск на текущей версии")
        }
    }

    override fun download(minecraft: MinecraftContext) {
        val info = getDownloaderInfo(minecraft)
        val base = info.modpackDirectory ?: minecraft.getDirectory()

        if (changes.isEmpty()) {
            // Nothing to apply, but a newer (possibly empty) bucket may have advanced the timestamp.
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
            plan.toDelete.forEach { file ->
                if (file.exists() && !file.deleteRecursively()) {
                    System.err.println("Не удалось удалить $file")
                }
            }
        }

        val toDownload = plan.toDownload
        if (toDownload.isEmpty()) {
            persistTimestamp(info)
            return
        }

        minecraft.progressMonitor.setStatus("Загружаем модпак...")
        minecraft.progressMonitor.setMax(toDownload.size)
        minecraft.progressMonitor.setProgress(0)

        val downloadedFiles = AtomicInteger(0)
        val failures = runBlocking {
            val dispatcher = Dispatchers.IO.limitedParallelism(DOWNLOAD_PARALLELISM)
            toDownload.map { (key, file) ->
                async(dispatcher) {
                    runCatching {
                        val url = UriEncodeUtils.encodePath(joinUrl(host, key), Charsets.UTF_8)
                        HttpDownloader.instance.downloadToFile(url, file)
                        val done = downloadedFiles.incrementAndGet()
                        progressMutex.withLock {
                            minecraft.progressMonitor.setStatus("Загружено $done/${toDownload.size}")
                            minecraft.progressMonitor.setProgress(done)
                        }
                    }.exceptionOrNull()?.let { key to it }
                }
            }.awaitAll().filterNotNull()
        }

        if (failures.isNotEmpty()) {
            failures.forEach { (key, error) ->
                System.err.println("Не удалось загрузить $key")
                error.printStackTrace()
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
        const val DOWNLOAD_PARALLELISM = 64
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
