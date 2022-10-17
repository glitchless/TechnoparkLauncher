package ru.lionzxy.tplauncher.prepare.downloader

import nu.redpois0n.oslib.Arch
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.minecraft.APPLE_SILICON_UPDATE_HOST_LINK
import ru.lionzxy.tplauncher.minecraft.APPLE_SILICON_UPDATE_JSON_LINK
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloader
import ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloaderInfo
import ru.lionzxy.tplauncher.utils.ConfigHelper

class NativeAppleSiliconDownloader : IncrementalDownloader() {
    override fun getDownloaderInfo(minecraft: MinecraftContext): IncrementalDownloaderInfo {
        return IncrementalDownloaderInfo(
            key = "asworkaround",
            updateJsonLink = APPLE_SILICON_UPDATE_JSON_LINK,
            updateHostLink = APPLE_SILICON_UPDATE_HOST_LINK,
            modpackDirectory = ConfigHelper.getAppleSiliconWorkaroundDirectory()
        )
    }

    override fun shouldDownload(minecraft: MinecraftContext): Boolean {
        val os = OperatingSystem.getOperatingSystem()
        println("Detect os is ${os.detailedString}")
        // Use only for macOs
        if (os.type != OperatingSystem.MACOS) {
            return false
        }

        return os.arch == Arch.ARM
    }
}
