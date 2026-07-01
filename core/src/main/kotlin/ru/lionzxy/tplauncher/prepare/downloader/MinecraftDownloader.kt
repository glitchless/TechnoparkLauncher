package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.connectivity.shouldTolerateInstallFailure
import sk.tomsik68.mclauncher.api.versions.IVersion
import java.io.File

class MinecraftDownloader() : IDownloader {
    var version: IVersion? = null
    override fun init(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Получение версии Minecraft...")
        version = minecraft.minecraftLauncher.getVersion()
    }

    override fun download(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Загрузка файлов Minecraft")
        try {
            version?.installer?.install(
                version,
                minecraft.minecraftAccountManager.minecraftInstance,
                minecraft.progressMonitor
            )
        } catch (e: Exception) {
            // The installer touches the network even for a fully-installed pack (it re-fetches the
            // lwjgl natives artifact every launch); offline that must not abort an installed pack.
            val versionId = minecraft.modpack.version
            val versionJson = File(minecraft.getDirectory(), "versions/$versionId/$versionId.json")
            if (!shouldTolerateInstallFailure(e, versionJson.exists(), minecraft.tolerateConnectivityBlock)) {
                throw e
            }
            Logger.w("Launcher", "Install step failed offline; launching installed files as-is", e)
        }
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true
}
