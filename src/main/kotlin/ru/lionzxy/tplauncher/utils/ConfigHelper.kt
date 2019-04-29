package ru.lionzxy.tplauncher.utils

import com.google.gson.Gson
import ru.lionzxy.tplauncher.config.Config
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

        gson.fromJson(configJson.readText(), Config::class.java)
    }

    init {
        getTemporaryDirectory().deleteDirectoryRecursionJava6()
    }

    fun writeToConfig(op: (Config.() -> Unit)) {
        op.invoke(config)
        configJson.writeText(gson.toJson(config))
    }

    fun getDefaultDirectory(): File {
        val dir = File(Platform.getCurrentPlatform().workingDirectory, "technomain")
        dir.mkdirs()
        return dir
    }

    fun getTemporaryDirectory(): File {
        val dir = File(getDefaultDirectory(), "tmp")
        dir.mkdirs()
        return dir
    }

    fun getJavaDirectory(): File {
        val dir = File(getDefaultDirectory(), "jre")
        dir.mkdirs()
        return dir
    }
}