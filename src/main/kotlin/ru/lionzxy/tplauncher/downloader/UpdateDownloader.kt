package ru.lionzxy.tplauncher.downloader

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.MinecraftModpack
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import java.io.File

const val UPDATER_JSON_URL = "https://minecraft.glitchless.ru/minecraft_dist/first_server/changelog.json"
const val HOST_URL = "https://minecraft.glitchless.ru/minecraft_dist/first_server/"

class UpdateDownloader : IDownloader {
    val changes = HashMap<String, Action>()
    val gson = Gson()
    var lastChangeTimestamp = 0L

    override fun init(progressMonitor: IProgressMonitor) {
        progressMonitor.setStatus("Получение списка обновлений с сервера...")
        val lastUpdateTimestamp =
            ConfigHelper.config.modpackDownloadedInfo[MinecraftModpack.MIDGARD]?.lastUpdateFromChangeLog ?: 0
        val json = HttpUtils.httpGet(UPDATER_JSON_URL)
        val type = object : TypeToken<Map<String, Map<String, Action>>>() {}.type
        val map = gson.fromJson<Map<String, Map<String, Action>>>(json, type)
        val changeLog = map.map { it.key.toLong() to it.value }
            .filter { it.first > lastUpdateTimestamp }.sortedBy { it.first }
        lastChangeTimestamp = changeLog.lastOrNull()?.first ?: 0
        changeLog.forEach { changes.putAll(it.second) }
    }

    override fun download(progressMonitor: IProgressMonitor) {
        progressMonitor.setStatus("Начинаем загружать обновления...")
        val base = ConfigHelper.getMinecraftDirectory(MinecraftModpack.MIDGARD)
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
                progressMonitor.setStatus("Загрузка ${it.key}")
                FileUtils.downloadFileWithProgress(HOST_URL + it.key, file, progressMonitor)
            }
        }
        if (lastChangeTimestamp <= 0) {
            return
        }
        ConfigHelper.writeToConfig {
            modpackDownloadedInfo[MinecraftModpack.MIDGARD]?.lastUpdateFromChangeLog = lastChangeTimestamp
        }
    }

    override fun shouldDownload() = true
}

enum class Action(code: Int) {
    @SerializedName("0")
    REMOVE(0),
    @SerializedName("1")
    ADD(1)
}