package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext

interface IDownloader {
    fun init(minecraft: MinecraftContext)
    fun download(minecraft: MinecraftContext)
    fun shouldDownload(minecraft: MinecraftContext): Boolean
}
