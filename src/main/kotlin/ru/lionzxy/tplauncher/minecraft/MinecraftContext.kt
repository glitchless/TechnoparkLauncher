package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File

const val BASE_URL = "https://minecraft.glitchless.ru"
const val APPLE_SILICON_UPDATE_HOST_LINK = "$BASE_URL/incremental/asworkaround/"
const val APPLE_SILICON_UPDATE_JSON_LINK = "$BASE_URL/incremental/asworkaround_changelog.json"

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
        updateJsonLink = "$BASE_URL/incremental/vanilla_changelog.json",
        updateHostLink = "$BASE_URL/incremental/vanilla",
        defaultServer = ServerInfo("mc.glitchless.ru", "Vanilla Server", null, 25566),
        version = "1.16.5-forge-36.0.0"
    ),
    GTNH(
        modpackName = "NewHorizon",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/gtnh_changelog.json",
        updateHostLink = "$BASE_URL/incremental/gtnh",
        defaultServer = null,
        version = "1.7.10-Forge10.13.4.1614-1.7.10"
    ),
    RLCRAFT(
        modpackName = "RLCraft",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/rlcraft_changelog.json",
        updateHostLink = "$BASE_URL/incremental/rlcraft",
        defaultServer = ServerInfo("mc.glitchless.ru", "RLCraft Server", null, 25569),
        version = "1.12.2-forge1.12.2-14.23.5.2847"
    );

    override fun toString(): String {
        return modpackName
    }

    fun isOldVersion(): Boolean {
        return version.startsWith("1.12") || version.startsWith("1.7") //FIXME Dirty hack
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
        MinecraftLauncher(this).launch(minecraftAccountManager.session!!)
    }

    fun getMinecraftInstance(): MinecraftInstance {
        return MinecraftInstance(getDirectory())
    }
}
