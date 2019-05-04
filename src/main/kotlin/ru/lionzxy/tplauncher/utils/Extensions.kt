package ru.lionzxy.tplauncher.utils

import javafx.application.Platform
import java.io.File

inline fun runOnUi(crossinline invoke: () -> Unit) {
    Platform.runLater { invoke.invoke() }
}

inline fun runAsync(crossinline invoke: () -> Unit) {
    tornadofx.runAsync {
        try {
            invoke.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun File.setWritableToFolder() {
    if (isDirectory) {
        val entries = listFiles()
        if (entries != null) {
            for (entry in entries) {
                entry.setWritableToFolder()
            }
        }
    }
    setWritable(true, false)
}

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