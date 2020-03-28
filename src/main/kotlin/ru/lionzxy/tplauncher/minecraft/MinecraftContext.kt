package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File

enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String,
    val updateJsonLink: String,
    val updateHostLink: String
) {
    MIDGARD(
        "Midgard",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/initial.zip",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/changelog.json",
        "https://minecraft.glitchless.ru/minecraft_dist/first_server/"
    )
}

class MinecraftContext(
    val progressMonitor: IProgressMonitor,
    val modpack: MinecraftModpack,
    val minecraftAccountManager: MinecraftAccountManager
) {
    fun getDirectory(): File {
        return ConfigHelper.getMinecraftDirectory(modpack)
    }
}
