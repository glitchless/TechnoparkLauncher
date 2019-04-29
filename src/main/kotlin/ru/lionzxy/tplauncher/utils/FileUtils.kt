package ru.lionzxy.tplauncher.utils

import java.io.File

fun File.createWithMkDirs(initialContent: String) {
    parentFile.mkdirs()

    if (exists()) {
        delete()
    }

    if (!createNewFile()) {
        return
    }

    writeText(initialContent)
}