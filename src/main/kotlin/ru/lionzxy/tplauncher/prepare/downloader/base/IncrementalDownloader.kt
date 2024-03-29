package ru.lionzxy.tplauncher.prepare.downloader.base

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.IDownloader
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.EmptyMonitoring
import ru.lionzxy.tplauncher.utils.UriEncodeUtils
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

abstract class IncrementalDownloader : IDownloader {
    private val changes = HashMap<String, Action>()
    private val gson = Gson()
    private var lastChangeTimestamp = 0L
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val downloadDispatcher = newFixedThreadPoolContext(nThreads = 64, name = "minecraft_downloader")
    private val mutex = Mutex()

    override fun init(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Получение списка обновлений с сервера...")
        val downloaderInfo = getDownloaderInfo(minecraft)
        val lastUpdateTimestamp =
            ConfigHelper.config.modpackDownloadedInfo[downloaderInfo.key]?.lastUpdateFromChangeLog ?: 0
        val url = downloaderInfo.updateJsonLink
        if (url.isNullOrEmpty()) {
            return
        }
        val json = HttpUtils.httpGet(url)
        val type = object : TypeToken<Map<String, Map<String, Action>>>() {}.type
        val map = gson.fromJson<Map<String, Map<String, Action>>>(json, type)
        val changeLog = map.map { it.key.toLong() to it.value }
            .filter { it.first > lastUpdateTimestamp }.sortedBy { it.first }
        lastChangeTimestamp = changeLog.lastOrNull()?.first ?: 0
        changeLog.forEach { changes.putAll(it.second) }
    }

    override fun download(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Начинаем загружать обновления...")
        val downloaderInfo = getDownloaderInfo(minecraft)
        val base = downloaderInfo.modpackDirectory ?: minecraft.getDirectory()
        if (changes.isEmpty()) {
            return
        }

        minecraft.progressMonitor.setStatus("Удаляем не нужные файлы...")
        val toDelete = changes.filter { it.value == Action.REMOVE }
        toDelete.forEach { File(base, it.key).delete() }

        val toDownload = changes.filter { it.value == Action.ADD }.map { it.key to File(base, it.key) }
        minecraft.progressMonitor.setStatus("Загружаем модпак...")
        minecraft.progressMonitor.setMax(toDownload.size)
        minecraft.progressMonitor.setProgress(0)
        val downloadedFiles = AtomicInteger(0)
        val debugCounter = AtomicInteger(0)
        var exceptions = emptyList<Throwable>()
        val downloadJob = coroutineScope.launch {
            // Create folders
            toDownload.forEach { (_, file) ->
                if (file.parentFile.exists()) {
                    file.parentFile.mkdirs()
                }
            }
            // Download
            exceptions = toDownload.map { (path, file) ->
                async {
                    runBlocking(downloadDispatcher) {
                        runCatching {
                            file.delete()
                            val url = downloaderInfo.updateHostLink + path
                            println("Start download ${debugCounter.incrementAndGet()}")

                            File(file.parent).mkdirs()

                            FileUtils.downloadFileWithProgress(
                                UriEncodeUtils.encodePath(url, Charset.forName("utf-8")),
                                file,
                                EmptyMonitoring()
                            )
                            val downloadedCount = downloadedFiles.incrementAndGet()
                            mutex.withLock {
                                withContext(Dispatchers.Main) {
                                    minecraft.progressMonitor.setStatus("Загружено $downloadedCount/${toDownload.size}")
                                    minecraft.progressMonitor.setProgress(downloadedCount)
                                }
                            }
                        }.exceptionOrNull()
                    }
                }
            }.mapNotNull { it.await() }
        }

        runBlocking {
            downloadJob.join()
        }
        if (exceptions.isNotEmpty()) {
            exceptions.forEach {
                it.printStackTrace()
            }
            throw exceptions.first()
        }

        if (lastChangeTimestamp <= 0) {
            return
        }
        ConfigHelper.writeToConfig {
            val downloadedInfo = modpackDownloadedInfo[downloaderInfo.key] ?: DownloadedInfo()
            downloadedInfo.lastUpdateFromChangeLog = lastChangeTimestamp
            modpackDownloadedInfo[downloaderInfo.key] = downloadedInfo
        }

    }

    override fun shouldDownload(minecraft: MinecraftContext) = true

    abstract fun getDownloaderInfo(minecraft: MinecraftContext): IncrementalDownloaderInfo
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

enum class Action(code: Int) {
    @SerializedName("0")
    REMOVE(0),

    @SerializedName("1")
    ADD(1)
}
