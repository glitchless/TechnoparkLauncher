package ru.lionzxy.tplauncher.downloader

import net.lingala.zip4j.core.ZipFile
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.util.FileUtils
import java.io.File

private const val DOWNLOAD_URL = "https://minecraft.glitchless.ru/minecraft_dist/first_server/initial.zip"

class InitialDownloader : IDownloader {
    override fun init(progressMonitor: IProgressMonitor) {}

    override fun download(progressMonitor: IProgressMonitor) {
        val dist = File(ConfigHelper.getTemporaryDirectory(), "minecraft.zip")
        progressMonitor.setStatus("Загрузка модов...")
        FileUtils.downloadFileWithProgress(
            DOWNLOAD_URL,
            dist,
            progressMonitor
        )
        val zipFile = ZipFile(dist)
        progressMonitor.setStatus("Разархивирование модов...")
        progressMonitor.setProgress(-1)
        zipFile.extractAll(ConfigHelper.getDefaultDirectory().absolutePath)
        markDownloaded()
    }

    private fun markDownloaded() {
        ConfigHelper.writeToConfig {
            downloadFirstPack = true
        }
    }

    override fun shouldDownload(): Boolean {
        return !(ConfigHelper.config.downloadFirstPack ?: false)
    }
}