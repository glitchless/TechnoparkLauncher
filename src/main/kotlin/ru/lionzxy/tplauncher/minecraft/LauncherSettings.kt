package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.config.Settings
import sk.tomsik68.mclauncher.api.common.ILaunchSettings
import java.io.File

class LauncherSettings(val settings: Settings) : ILaunchSettings {
    override fun isModifyAppletOptions() = false

    override fun getCustomParameters() = mutableMapOf<String, String>()

    override fun getCommandPrefix(): MutableList<String> {
        return settings.commandPrefix.split(" ").toMutableList()
    }

    override fun getJavaArguments(): MutableList<String> {
        return settings.customJavaParameter.split(" ").toMutableList()
    }

    override fun getJavaLocation(): File? {
        return settings.javaLocation?.let { File(it) }
    }

    override fun getInitHeap() = "256M"

    override fun getHeap() = settings.heapSize

}
