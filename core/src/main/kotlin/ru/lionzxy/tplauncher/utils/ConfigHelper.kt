package ru.lionzxy.tplauncher.utils

import com.google.gson.Gson
import ru.lionzxy.tplauncher.config.Config
import ru.lionzxy.tplauncher.config.DownloadedInfo
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import sk.tomsik68.mclauncher.impl.common.Platform
import java.io.File

object ConfigHelper {
    private val gson = Gson()
    private val configJson = File(getDefaultDirectory(), "profile.json")
    public val config: Config by lazy {
        if (!configJson.exists()) {
            val internalConfig = Config()
            configJson.createWithMkDirs(gson.toJson(internalConfig))
            return@lazy internalConfig
        }

        val config = gson.fromJson(configJson.readText(), Config::class.java)
        MinecraftModpack.values().filter { config.modpackDownloadedInfo[it.modpackName] == null }
            .forEach { config.modpackDownloadedInfo[it.modpackName] = DownloadedInfo() }
        if (config.currentModpack == null) {
            config.currentModpack = MinecraftModpack.VANILLA
        }
        return@lazy config
    }

    init {
        getTemporaryDirectory().deleteDirectoryRecursionJava6()
    }

    fun writeToConfig(op: (Config.() -> Unit)) {
        op.invoke(config)
        configJson.writeText(gson.toJson(config))
    }

    fun getDefaultDirectory(): File {
        val dir = File(Platform.getCurrentPlatform().workingDirectory, "technomine")
        dir.mkdirs()
        return dir
    }

    fun getAppleSiliconWorkaroundDirectory(): File {
        val dir = File(getDefaultDirectory(), "asworkaround")
        dir.mkdirs()
        return dir
    }

    fun getMinecraftDirectory(modpack: MinecraftModpack): File {
        val file = File(getDefaultDirectory(), modpack.modpackName.lowercase())
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

    fun getLogsDirectory(): File {
        val dir = File(getDefaultDirectory(), "logs")
        dir.mkdirs()
        return dir
    }

    fun getJavaDirectory(): File {
        val dir = File(getDefaultDirectory(), "jre")
        dir.mkdirs()
        return dir
    }

    fun getJreInstallDirectory(code: String): File {
        val dir = File(getJavaDirectory(), code)
        dir.mkdirs()
        return dir
    }

    fun getJreManifestCacheFile(): File {
        return File(getJavaDirectory(), "jres2.json")
    }

    fun getLogoFile(): File {
        return File(getDefaultDirectory(), "logo.png");
    }
}
