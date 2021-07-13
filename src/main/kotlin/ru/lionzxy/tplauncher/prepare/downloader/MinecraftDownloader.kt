package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.MinecraftLauncher
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
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
            MinecraftInstance(ConfigHelper.getMinecraftDirectory(minecraft.modpack)),
            minecraft.progressMonitor
        )
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true
}
