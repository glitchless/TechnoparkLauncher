package ru.lionzxy.tplauncher.utils

import java.net.URL

object ResourceHelper {
    fun getResource(path: String): URL =
        javaClass.getResource("/$path") ?: error("Resource not found on classpath: /$path")
}
