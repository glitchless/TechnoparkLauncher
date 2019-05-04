package ru.lionzxy.tplauncher.config

import nu.redpois0n.oslib.Arch
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.exceptions.HeapSizeInvalidException
import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.io.File

class Settings() {
    var heapSize = if (OperatingSystem.getOperatingSystem().arch == Arch.x86) "1G" else "3G"
        set(value) {
            if (!"[0-9]*[G|g|M|m]".toRegex().matches(value)) {
                throw HeapSizeInvalidException(value)
            }
        }
    var customJavaParameter = getDefaultJavaArguments()
    var commandPrefix = getDefaultCommandPrefix()
    var javaLocation = getDefaultJavaLocation()

    private fun getDefaultJavaArguments(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        return "-XX:+UseG1GC -XX:ConcGCThreads=${cores / 4} -XX:ParallelGCThreads=$cores"
    }

    private fun getDefaultCommandPrefix() =
        if (OperatingSystem.getOperatingSystem().isUnix) "nohup" else "cmd.exe /C start"

    private fun getDefaultJavaLocation(): String? {
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