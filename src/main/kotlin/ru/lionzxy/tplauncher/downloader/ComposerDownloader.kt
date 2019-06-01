package ru.lionzxy.tplauncher.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.setWritableToFolder
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class ComposerDownloader(minecraftAccountManager: MinecraftAccountManager) {
    val downloaders = listOf(InitialDownloader(), UpdateDownloader(), MinecraftDownloader(minecraftAccountManager))

    @Throws(Exception::class)
    fun downloadAll(progressMonitor: IProgressMonitor) {
        downloaders.forEach {
            if (it.shouldDownload()) {
                download(it, progressMonitor)
            }
        }
        ConfigHelper.getDefaultDirectory().setWritableToFolder()
    }

    private fun download(downloader: IDownloader, progressMonitor: IProgressMonitor) {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        progressMonitor.setProgress(-1)
        downloader.init(progressMonitor)
        downloader.download(progressMonitor)
    }
}