package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.MinecraftLauncher
import sk.tomsik68.mclauncher.api.versions.IVersion

class MinecraftDownloader() : IDownloader {
    var version: IVersion? = null
    override fun init(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Получение версии Minecraft...")
        version = MinecraftLauncher(minecraft).getVersion()
    }

    override fun download(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Загрузка файлов Minecraft")
        version?.installer?.install(
            version,
            minecraft.minecraftAccountManager.minecraftInstance,
            minecraft.progressMonitor
        )
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true
}
