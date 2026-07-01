package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File

const val BASE_URL = "https://minecraft.glitchless.ru"
const val APPLE_SILICON_UPDATE_HOST_LINK = "$BASE_URL/incremental/asworkaround/"
const val APPLE_SILICON_UPDATE_JSON_LINK = "$BASE_URL/incremental/asworkaround_changelogv2.json"
const val JRES_JSON_LINK = "$BASE_URL/jres2.json"

enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String?,
    val updateJsonLink: String?,
    val updateHostLink: String?,
    val defaultServer: ServerInfo?,
    val version: String,
    val javaCode: String = "jre8",
) {
    VANILLA(
        modpackName = "Vanilla",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/vanilla_changelogv2.json",
        updateHostLink = "$BASE_URL/incremental/vanilla",
        defaultServer = ServerInfo("mc.glitchless.ru", "Vanilla Server", null, 25566),
        version = "1.16.5-forge-36.0.0"
    ),
    GTNH(
        modpackName = "NewHorizon",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/gtnh_changelogv2.json",
        updateHostLink = "$BASE_URL/incremental/gtnh",
        defaultServer = null,
        version = "1.7.10-Forge10.13.4.1614-1.7.10"
    ),
    NOMI(
        modpackName = "Nomifactory",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/nomi_changelogv2.json",
        updateHostLink = "$BASE_URL/incremental/nomi",
        defaultServer = ServerInfo("mc.glitchless.ru", "Nomi Server", null, 25568),
        version = "1.12.2-forge-14.23.5.2860"
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
    // One launcher per context so the resolved version is cached across prepare
    // (MinecraftDownloader.init) and launch() — otherwise the online manifest fetch runs twice
    // per launch, and twice as slowly when the network is blocked/black-holed.
    val minecraftLauncher: MinecraftLauncher by lazy { MinecraftLauncher(this) }

    /**
     * When true (set for an explicit retry from the ConnectivityBlocked screen), a firewall/AV
     * WSAEACCES block during install of an already-installed pack is tolerated and the launch
     * proceeds offline, instead of re-raising the repair screen.
     */
    @Volatile
    var tolerateConnectivityBlock: Boolean = false

    fun getDirectory(): File {
        return ConfigHelper.getMinecraftDirectory(modpack)
    }

    fun launch() {
        minecraftLauncher.launch(minecraftAccountManager.session!!)
    }

    fun getMinecraftInstance(): MinecraftInstance {
        return MinecraftInstance(getDirectory())
    }
}
