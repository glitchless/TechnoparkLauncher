package ru.lionzxy.tplauncher.downloader

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.util.FileUtils
import sk.tomsik68.mclauncher.util.HttpUtils
import java.io.File

const val UPDATER_JSON_URL = "https://minecraft.glitchless.ru/minecraft_dist/first_server/changelog.json"
const val HOST_URL = "https://minecraft.glitchless.ru/minecraft_dist/first_server/"

class Updater {
    val changes = HashMap<String, Action>()
    val gson = Gson()
    var lastChangeTimestamp = 0L

    fun initUpdater() {
        val lastUpdateTimestamp = ConfigHelper.config.lastUpdateFromChangeLog ?: 0
        val json = HttpUtils.httpGet(UPDATER_JSON_URL)
        val type = object : TypeToken<Map<String, Map<String, Action>>>() {}.type
        val map = gson.fromJson<Map<String, Map<String, Action>>>(json, type)
        val changeLog = map.map { it.key.toLong() to it.value }
            .filter { it.first > lastUpdateTimestamp }.sortedBy { it.first }
        lastChangeTimestamp = changeLog.lastOrNull()?.first ?: 0
        changeLog.forEach { changes.putAll(it.second) }
    }

    fun update(monitor: IProgressMonitor) {
        val base = ConfigHelper.getDefaultDirectory()
        changes.forEach {
            val file = File(base, it.key)
            if (it.value == Action.REMOVE) {
                file.delete()
            } else if (it.value == Action.ADD) {
                file.delete()
                monitor.setStatus("Downloading ${it.key}")
                FileUtils.downloadFileWithProgress(HOST_URL + it.key, file, monitor)
            }
        }
        ConfigHelper.writeToConfig {
            lastUpdateFromChangeLog = lastChangeTimestamp
        }
    }
}

enum class Action(code: Int) {
    @SerializedName("0")
    REMOVE(0),
    @SerializedName("1")
    ADD(1)
}