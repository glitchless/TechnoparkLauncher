package ru.lionzxy.tplauncher.downloader

import sk.tomsik68.mclauncher.api.common.ILaunchSettings
import java.io.File
import java.util.Arrays.asList


class DefaultLaunchSettings : ILaunchSettings {
    override fun getInitHeap(): String {
        return "256M"
    }

    override fun getHeap(): String {
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
        return null
    }

    override fun isModifyAppletOptions(): Boolean {
        return false
    }
}