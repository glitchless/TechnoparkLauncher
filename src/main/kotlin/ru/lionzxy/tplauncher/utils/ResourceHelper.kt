package ru.lionzxy.tplauncher.utils

import java.net.URL

object ResourceHelper {
    fun getResource(path: String): URL {
        return javaClass.getResource("/$path")
    }
}