package ru.lionzxy.tplauncher.prepare

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.downloader.ComposerDownloader
import ru.lionzxy.tplauncher.prepare.processing.MergeOptionsProcessing

class ComposePrepare : IPrepareTask {
    private val prepares = listOf(ComposerDownloader(), MergeOptionsProcessing())

    override fun prepareMinecraft(minecraft: MinecraftContext) {
        prepares.forEach {
            it.prepareMinecraft(minecraft)
        }
    }

}
