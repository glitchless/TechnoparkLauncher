package ru.lionzxy.tplauncher.prepare.downloader

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import java.io.File

class UpdateDownloader : IDownloader {
    val changes = HashMap<String, Action>()
    val gson = Gson()
    var lastChangeTimestamp = 0L

    override fun init(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Получение списка обновлений с сервера...")
        val lastUpdateTimestamp =
            ConfigHelper.config.modpackDownloadedInfo[minecraft.modpack.modpackName]!!.lastUpdateFromChangeLog ?: 0
        val json = HttpUtils.httpGet(minecraft.modpack.updateJsonLink)
        val type = object : TypeToken<Map<String, Map<String, Action>>>() {}.type
        val map = gson.fromJson<Map<String, Map<String, Action>>>(json, type)
        val changeLog = map.map { it.key.toLong() to it.value }
            .filter { it.first > lastUpdateTimestamp }.sortedBy { it.first }
        lastChangeTimestamp = changeLog.lastOrNull()?.first ?: 0
        changeLog.forEach { changes.putAll(it.second) }
    }

    override fun download(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Начинаем загружать обновления...")
        val base = minecraft.getDirectory()
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
                minecraft.progressMonitor.setStatus("Загрузка ${it.key}")
                FileUtils.downloadFileWithProgress(
                    minecraft.modpack.updateHostLink + it.key,
                    file,
                    minecraft.progressMonitor
                )
            }
        }
        if (lastChangeTimestamp <= 0) {
            return
        }
        ConfigHelper.writeToConfig {
            modpackDownloadedInfo[minecraft.modpack.modpackName]!!.lastUpdateFromChangeLog = lastChangeTimestamp
        }
    }

    override fun shouldDownload(minecraft: MinecraftContext) = false
}

enum class Action(code: Int) {
    @SerializedName("0")
    REMOVE(0),
    @SerializedName("1")
    ADD(1)
}
