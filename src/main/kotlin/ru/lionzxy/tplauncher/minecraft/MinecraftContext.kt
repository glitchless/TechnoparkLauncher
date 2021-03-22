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
        initialDownloadLink = "$BASE_URL/initial/vanilla.zip",
        updateJsonLink = "$BASE_URL/incremental/vanilla_changelog.json",
        updateHostLink = "$BASE_URL/incremental/vanilla",
        defaultServer = ServerInfo("mc.glitchless.ru", "Vanilla Server", null, 25566),
        version = "1.16.5-forge-36.0.0"
    ),
    HEAVY(
        modpackName = "Heavy",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/heavy_changelog.json",
        updateHostLink = "$BASE_URL/incremental/heavy",
        defaultServer = ServerInfo("mc.glitchless.ru", "Heavy Server", null, 25567),
        version = "1.16.5-forge-36.0.43"
    ),
    SKYBLOCK(
        modpackName = "Skyblock",
        initialDownloadLink = null,
        updateJsonLink = null,
        updateHostLink = null,
        defaultServer = ServerInfo("mc.glitchless.ru", "Heavy Server", null, 25567),
        version = "1.12.2-forge1.12.2-14.23.5.2847"
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
        MinecraftLauncher(this).launch(minecraftAccountManager.session!!)
    }

    fun getMinecraftInstance(): MinecraftInstance {
        return MinecraftInstance(getDirectory())
    }
}
