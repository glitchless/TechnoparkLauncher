package ru.lionzxy.tplauncher.prepare.downloader.base

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.IDownloader
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.UriEncodeUtils
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import java.io.File
import java.nio.charset.Charset

abstract class IncrementalDownloader : IDownloader {
    private val changes = HashMap<String, Action>()
    private val gson = Gson()
    private var lastChangeTimestamp = 0L

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
        changes.forEach {
            val file = File(base, it.key)
            if (it.value == Action.REMOVE) {
                file.delete()
            } else if (it.value == Action.ADD) {
                file.delete()
                if (file.parentFile.exists()) {
                    file.parentFile.mkdirs()
                }
                minecraft.progressMonitor.setStatus("Загрузка ${file.name}")
                val url = downloaderInfo.updateHostLink + it.key
                FileUtils.downloadFileWithProgress(
                    UriEncodeUtils.encodePath(url, Charset.forName("utf-8")),
                    file,
                    minecraft.progressMonitor
                )
            }
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

    override fun shouldDownload(minecraft: MinecraftContext) = false

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
