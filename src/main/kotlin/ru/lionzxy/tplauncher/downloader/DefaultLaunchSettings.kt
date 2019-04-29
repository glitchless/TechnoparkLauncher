package ru.lionzxy.tplauncher.downloader

import nu.redpois0n.oslib.Arch
import nu.redpois0n.oslib.OperatingSystem
import sk.tomsik68.mclauncher.api.common.ILaunchSettings
import java.io.File
import java.util.Arrays.asList


class DefaultLaunchSettings(val jreFile: File? = null) : ILaunchSettings {
    override fun getInitHeap(): String {
        return "256M"
    }

    override fun getHeap(): String {
        if (OperatingSystem.getOperatingSystem().arch == Arch.x86) {
            return "1G"
        }
        return "3G"
    }

    override fun getCustomParameters(): Map<String, String>? {
        return null
    }

    override fun getJavaArguments(): List<String>? {
        val cores = Runtime.getRuntime().availableProcessors()
        return asList(
            "-XX:+UseG1GC",
            "-XX:ConcGCThreads=" + cores / 4,
            "-XX:ParallelGCThreads=$cores"
        )
    }

    override fun getCommandPrefix(): List<String>? {
        return null
    }

    override fun getJavaLocation(): File? {
        return jreFile
    }

    override fun isModifyAppletOptions(): Boolean {
        return false
    }
}