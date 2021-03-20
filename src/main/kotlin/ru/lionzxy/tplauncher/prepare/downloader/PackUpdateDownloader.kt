package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloader
import ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloaderInfo

class UpdateDownloader : IncrementalDownloader() {
    override fun getDownloaderInfo(minecraft: MinecraftContext) =
        IncrementalDownloaderInfo(
            key = minecraft.modpack.modpackName,
            updateJsonLink = minecraft.modpack.updateJsonLink,
            updateHostLink = minecraft.modpack.updateHostLink
        )
}
