package ru.lionzxy.tplauncher.config

import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.OperatingSystem
import ru.lionzxy.tplauncher.utils.SystemMemoryHelper
import java.io.File

object SettingsDefault {
    private const val MEGABYTE = 1024L * 1024L

    fun getDefaultJavaArguments(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        return "-XX:+UseG1GC -XX:ConcGCThreads=${cores / 4} -XX:ParallelGCThreads=$cores"
    }

    fun getDefaultHeapSize(): String {
        var totalBytes: Long? = null
        try {
            totalBytes = SystemMemoryHelper.getSystemTotalMemory()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (totalBytes == null) {
            return "3G"
        }
        val totalMB = (totalBytes * 8) / (MEGABYTE * 10) // (totalBytes/MEGABYTE)*(8/10)

        return "${totalMB.coerceAtLeast(3 * 1024)}M"
    }

    fun getDefaultCommandPrefix() =
        if (OperatingSystem.current() == OperatingSystem.LINUX) "/usr/bin/nohup" else "cmd.exe /C start"

    fun getDefaultJavaLocation(): String? {
        if (!ConfigHelper.getJREPathFile().exists()) {
            return null
        }
        var jrePath = File(ConfigHelper.getJREPathFile().readText())
        if (OperatingSystem.current() == OperatingSystem.WINDOWS) {
            jrePath = File(jrePath.parent, "javaw.exe")
        }
        return jrePath.absolutePath
    }
}
