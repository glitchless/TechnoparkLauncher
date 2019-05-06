package ru.lionzxy.tplauncher.utils

import javafx.application.Platform
import javafx.event.EventTarget
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import tornadofx.imageview
import java.awt.Desktop
import java.io.File
import java.net.URI

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

fun String.openInBrowser() {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(this))
    }
}


fun EventTarget.svgview(path: String, op: ImageView.() -> Unit = {}): ImageView {
    return imageview(Image(ResourceHelper.getResource("icon/$path.svg").openStream()), op)
}