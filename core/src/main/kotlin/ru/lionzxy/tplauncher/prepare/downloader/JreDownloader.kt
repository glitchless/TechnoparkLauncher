package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.jre.JreManager

/**
 * Provisions the per-modpack managed JRE before any modpack content is downloaded. A failure here
 * aborts the launch (hard-fail) rather than falling back to a possibly-wrong system Java.
 */
class JreDownloader : IDownloader {
    override fun init(minecraft: MinecraftContext) {}

    override fun download(minecraft: MinecraftContext) {
        JreManager.instance.ensureInstalled(minecraft.modpack.javaCode, minecraft.progressMonitor)
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true
}
