package ru.lionzxy.tplauncher.services.config

import com.google.gson.Gson
import com.squareup.anvil.annotations.ContributesBinding
import ru.lionzxy.tplauncher.data.config.Config
import ru.lionzxy.tplauncher.data.config.DownloadedInfo
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.createWithMkDirs
import java.io.File
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class ConfigProviderImpl @Inject constructor() : ConfigProvider {
    private val gson = Gson()
    private val configJson = File(ConfigHelper.getDefaultDirectory(), "profile.json")

    override val config: Config by lazy {
        if (!configJson.exists()) {
            val internalConfig = Config()
            configJson.createWithMkDirs(gson.toJson(internalConfig))
            return@lazy internalConfig
        }

        val config = gson.fromJson(configJson.readText(), Config::class.java)
        MinecraftModpack.values().filter { config.modpackDownloadedInfo[it.modpackName] == null }
            .forEach { config.modpackDownloadedInfo[it.modpackName] = DownloadedInfo() }
        return@lazy config
    }

    override fun writeToConfig(op: (Config.() -> Unit)) {
        op.invoke(config)
        configJson.writeText(gson.toJson(config))
    }
}
