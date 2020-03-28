package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File

enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String,
    val updateJsonLink: String,
    val updateHostLink: String,
    val defaultServer: ServerInfo,
    val version: String
) {
    MIDGARD(
        "Midgard",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/initial.zip",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/changelog.json",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/",
        ServerInfo("minecraft.glitchless.ru", "Glitchless Server", null, 25565),
        "1.12.2-forge1.12.2-14.23.5.2836"
    ),
    LIGHT(
        "Light",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/initial.zip",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/changelog.json",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/",
        ServerInfo("minecraft.glitchless.ru", "Glitchless Server", null, 25565),
        "1.12.2-forge1.12.2-14.23.5.2836"
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
