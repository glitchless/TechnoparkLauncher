package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.IPrepareTask
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.setWritableToFolder

class ComposerDownloader() : IPrepareTask {
    val downloaders = listOf(InitialDownloader(), UpdateDownloader(), MinecraftDownloader())


    override fun prepareMinecraft(minecraft: MinecraftContext) {
        downloaders.forEach {
            if (it.shouldDownload(minecraft)) {
                download(it, minecraft)
            }
        }
        ConfigHelper.getDefaultDirectory().setWritableToFolder()
    }

    private fun download(downloader: IDownloader, minecraft: MinecraftContext) {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        minecraft.progressMonitor.setProgress(-1)
        downloader.init(minecraft)
        downloader.download(minecraft)
    }

}
