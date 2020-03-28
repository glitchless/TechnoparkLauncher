package ru.lionzxy.tplauncher.prepare.downloader

import net.lingala.zip4j.core.ZipFile
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.util.FileUtils
import java.io.File


class InitialDownloader : IDownloader {
    override fun init(minecraft: MinecraftContext) {}

    override fun download(minecraft: MinecraftContext) {
        val dist = File(ConfigHelper.getTemporaryDirectory(), "minecraft.zip")
        minecraft.progressMonitor.setStatus("Загрузка модов...")
        FileUtils.downloadFileWithProgress(
            minecraft.modpack.initialDownloadLink,
            dist,
            minecraft.progressMonitor
        )
        val zipFile = ZipFile(dist)
        minecraft.progressMonitor.setStatus("Разархивирование модов...")
        minecraft.progressMonitor.setProgress(-1)
        zipFile.extractAll(minecraft.getDirectory().absolutePath)
        markDownloaded(minecraft)
    }

    private fun markDownloaded(minecraft: MinecraftContext) {
        ConfigHelper.writeToConfig {
            modpackDownloadedInfo[minecraft.modpack]?.initFileDownload = true
        }
    }

    override fun shouldDownload(minecraft: MinecraftContext): Boolean {
        return !(ConfigHelper.config.modpackDownloadedInfo[minecraft.modpack]?.initFileDownload ?: false)
    }
}
