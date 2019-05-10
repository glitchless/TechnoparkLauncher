package ru.lionzxy.tplauncher.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.minecraft.MinecraftLauncher
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.api.versions.IVersion

class MinecraftDownloader(val minecraftAccountManager: MinecraftAccountManager) : IDownloader {
    var version: IVersion? = null
    override fun init(progressMonitor: IProgressMonitor) {
        progressMonitor.setStatus("Получение версии Minecraft...")
        version = MinecraftLauncher.getVersion(minecraftAccountManager.minecraftInstance)
    }

    override fun download(progressMonitor: IProgressMonitor) {
        progressMonitor.setStatus("Загрузка файлов Minecraft")
        version?.installer?.install(version, minecraftAccountManager.minecraftInstance, progressMonitor)
    }

    override fun shouldDownload() = true
}