package ru.lionzxy.tplauncher.minecraft

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
        initialDownloadLink = "https://minecraft.glitchless.ru/initial/vanilla.zip",
        updateJsonLink = "https://minecraft.glitchless.ru/incremental/vanilla_changelog.json",
        updateHostLink = "https://minecraft.glitchless.ru/incremental/vanilla",
        defaultServer = ServerInfo("mc1.glitchless.ru", "Glitchless Server", null, 25565),
        version = "1.16.5-forge-36.0.0"
    ),
    VALHELSIA(
        modpackName = "Valhelsia",
        initialDownloadLink = "https://minecraft.glitchless.ru/initial/valhelsia.zip",
        updateJsonLink = "https://minecraft.glitchless.ru/incremental/valhelsia_changelog.json",
        updateHostLink = "https://minecraft.glitchless.ru/incremental/valhelsia",
        defaultServer = ServerInfo("mcbig.glitchless.ru", "Glitchless Valhelsia Server", null, 25565),
        version = "1.16.5-forge-36.0.0"
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
