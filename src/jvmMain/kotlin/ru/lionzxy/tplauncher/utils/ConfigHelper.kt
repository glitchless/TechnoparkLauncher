package ru.lionzxy.tplauncher.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.lionzxy.tplauncher.config.Config
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.minecraft.model.MinecraftModpack
import java.io.File

object ConfigHelper {
    private val configJson = File(getDefaultDirectory(), "profile.json")
    val config: Config by lazy {
        if (!configJson.exists()) {
            val internalConfig = Config()
            configJson.createWithMkDirs(Json.encodeToString(internalConfig))
            return@lazy internalConfig
        }

        val config = Json.decodeFromString<Config>(configJson.readText())
        MinecraftModpack.entries.filter { config.modpackDownloadedInfo[it.modpackName] == null }
            .forEach { config.modpackDownloadedInfo[it.modpackName] = DownloadedInfo() }
        return@lazy config
    }

    init {
        getTemporaryDirectory().deleteDirectoryRecursionJava6()
    }

    fun writeToConfig(op: (Config.() -> Unit)) {
        op.invoke(config)
        configJson.writeText(Json.encodeToString(config))
    }

    fun getDefaultDirectory(): File {
        val minecraftDir = when (OperatingSystem.current()) {
            OperatingSystem.LINUX -> File(System.getProperty("user.home"), ".minecraft")
            OperatingSystem.MACOS -> File(System.getProperty("user.home"), "Library/Application Support/minecraft")
            OperatingSystem.WINDOWS -> {
                val appData = System.getenv("APPDATA")
                if (appData != null) {
                    File(appData, ".minecraft")
                } else {
                    File(System.getProperty("user.home"), ".minecraft")
                }
            }
        }
        val dir = File(minecraftDir, "technomine")
        dir.mkdirs()
        return dir
    }

    fun getAppleSiliconWorkaroundDirectory(): File {
        val dir = File(getDefaultDirectory(), "asworkaround")
        dir.mkdirs()
        return dir
    }

    fun getMinecraftDirectory(modpack: MinecraftModpack): File {
        val file = File(getDefaultDirectory(), modpack.modpackName.toLowerCase())
        file.mkdir()
        return file
    }

    fun getTemporaryDirectory(): File {
        val dir = File(getDefaultDirectory(), "tmp")
        dir.mkdirs()
        return dir
    }

    fun getBackupFolder(): File {
        val dir = File(getDefaultDirectory(), "backup")
        dir.mkdirs()
        return dir
    }

    fun getJavaDirectory(): File {
        val dir = File(getDefaultDirectory(), "jre")
        dir.mkdirs()
        return dir
    }

    fun getJREPathFile(): File {
        return File(getDefaultDirectory(), "jrepath.txt")
    }

    fun getLogoFile(): File {
        return File(getDefaultDirectory(), "logo.png");
    }

    fun writeJREConfig(path: String) {
        val jreFile = File(getDefaultDirectory(), "jrepath.txt")
        jreFile.createWithMkDirs(path)
    }
}
