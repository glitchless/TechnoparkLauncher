package ru.lionzxy.tplauncher.minecraft

import com.google.gson.annotations.SerializedName
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File

enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String?,
    val updateJsonLink: String?,
    val updateHostLink: String?,
    val defaultServer: ServerInfo?,
    val version: String
) {
    VANILLA(
        modpackName = "Vanilla",
        initialDownloadLink = null,
        updateJsonLink = null,
        updateHostLink = null,
        defaultServer = ServerInfo("mc1.glitchless.ru", "Glitchless Server", null, 25565),
        version = "1.16.5"
    );

    override fun toString(): String {
        return modpackName
    }
}

class MinecraftContext(
    val progressMonitor: IProgressMonitor,
    val modpack: MinecraftModpack,
    val minecraftAccountManager: MinecraftAccountManager
) {
    fun getDirectory(): File {
        return ConfigHelper.getMinecraftDirectory(modpack)
    }

    fun launch() {
        MinecraftLauncher.launch(this, minecraftAccountManager.session!!)
    }

    fun getMinecraftInstance(): MinecraftInstance {
        return MinecraftInstance(getDirectory())
    }
}
