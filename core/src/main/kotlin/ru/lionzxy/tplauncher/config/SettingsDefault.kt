package ru.lionzxy.tplauncher.config

import nu.redpois0n.oslib.Arch
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.SystemMemoryHelper
import java.io.File

object SettingsDefault {
    private const val MEGABYTE = 1024L * 1024L

    fun getDefaultJavaArguments(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        return "-XX:+UseG1GC -XX:ConcGCThreads=${cores / 4} -XX:ParallelGCThreads=$cores"
    }

    fun getDefaultHeapSize(): String {
        if (OperatingSystem.getOperatingSystem().arch == Arch.x86) {
            return "1G"
        }

        var totalBytes: Long? = null
        try {
            totalBytes = SystemMemoryHelper.getSystemTotalMemory()
        } catch (e: Exception) {
            Logger.w("Settings", "Failed to detect system memory; using default heap size", e)
        }

        if (totalBytes == null) {
            return "3G"
        }
        val totalMB = (totalBytes * 8) / (MEGABYTE * 10) // (totalBytes/MEGABYTE)*(8/10)

        return "${totalMB.coerceAtLeast(3 * 1024)}M"
    }

    // On Windows we launch javaw.exe directly via ProcessBuilder (CreateProcessW), which passes the
    // command line as lossless UTF-16. The old "cmd.exe /C start" prefix re-parsed the command line
    // through cmd/start, which mangled non-ASCII (e.g. Cyrillic) paths in the game dir / JRE location
    // and detached the child so its crash output never reached mcout.log/mcerr.log.
    fun getDefaultCommandPrefix() =
        if (OperatingSystem.getOperatingSystem().isUnix) "/usr/bin/nohup" else ""

    fun getDefaultJavaLocation(): String? {
        if (!ConfigHelper.getJREPathFile().exists()) {
            return null
        }
        var jrePath = File(ConfigHelper.getJREPathFile().readText())
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) {
            jrePath = File(jrePath.parent, "javaw.exe")
        }
        return jrePath.absolutePath
    }
}
