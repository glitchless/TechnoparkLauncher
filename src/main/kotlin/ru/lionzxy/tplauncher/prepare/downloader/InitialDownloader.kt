package ru.lionzxy.tplauncher.prepare.downloader

import org.zeroturnaround.zip.ZipUtil
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.TextProgressMonitor
import sk.tomsik68.mclauncher.util.FileUtils
import java.io.File


class InitialDownloader : IDownloader {
    override fun init(minecraft: MinecraftContext) {}

    override fun download(minecraft: MinecraftContext) {
        val dist = File(ConfigHelper.getTemporaryDirectory(), "minecraft.zip")
        minecraft.progressMonitor.setStatus("Загрузка модов...")
        val url = minecraft.modpack.initialDownloadLink
        if (url.isNullOrEmpty()) {
            markDownloaded(minecraft)
            return
        }
        FileUtils.downloadFileWithProgress(
            minecraft.modpack.initialDownloadLink,
            dist,
            TextProgressMonitor("Загрузка модов... %s", minecraft.progressMonitor)
        )

        minecraft.progressMonitor.setStatus("Разархивирование модов...")
        minecraft.progressMonitor.setProgress(-1)
        ZipUtil.unpack(dist, minecraft.getDirectory())
        markDownloaded(minecraft)
    }

    private fun markDownloaded(minecraft: MinecraftContext) {
        ConfigHelper.writeToConfig {
            modpackDownloadedInfo[minecraft.modpack.modpackName]!!.initFileDownload = true
        }
    }

    override fun shouldDownload(minecraft: MinecraftContext): Boolean {
        return !(ConfigHelper.config.modpackDownloadedInfo[minecraft.modpack.modpackName]!!.initFileDownload ?: false)
    }
}
