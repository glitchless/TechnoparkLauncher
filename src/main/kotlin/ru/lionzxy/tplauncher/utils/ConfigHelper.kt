package ru.lionzxy.tplauncher.utils

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import sk.tomsik68.mclauncher.impl.common.Platform
import java.io.File

object ConfigHelper {
    fun getDefaultDirectory(): File {
        val dir = File(Platform.getCurrentPlatform().workingDirectory, "technomine")
        dir.mkdirs()
        return dir
    }

    fun getAppleSiliconWorkaroundDirectory(): File {
        val dir = File(getDefaultDirectory(), "asworkaround")
        dir.mkdirs()
        return dir
    }

    fun getMinecraftDirectory(modpack: MinecraftModpack): File {
        val file = File(getDefaultDirectory(), modpack.modpackName.toLowerCase())
        file.mkdir()
        return file
    }

    fun getTemporaryDirectory(): File {
        val dir = File(getDefaultDirectory(), "tmp")
        dir.mkdirs()
        return dir
    }

    fun getBackupFolder(): File {
        val dir = File(getDefaultDirectory(), "backup")
        dir.mkdirs()
        return dir
    }

    fun getJavaDirectory(): File {
        val dir = File(getDefaultDirectory(), "jre")
        dir.mkdirs()
        return dir
    }

    fun getJREPathFile(): File {
        return File(getDefaultDirectory(), "jrepath.txt")
    }

    fun getLogoFile(): File {
        return File(getDefaultDirectory(), "logo.png");
    }

    fun writeJREConfig(path: String) {
        val jreFile = File(getDefaultDirectory(), "jrepath.txt")
        jreFile.createWithMkDirs(path)
    }
}
