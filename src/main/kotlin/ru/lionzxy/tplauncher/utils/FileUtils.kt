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

fun File.deleteDirectoryRecursionJava6() {
    if (isDirectory) {
        val entries = listFiles()
        if (entries != null) {
            for (entry in entries) {
                entry.deleteDirectoryRecursionJava6()
            }
        }
    }
    delete()
}