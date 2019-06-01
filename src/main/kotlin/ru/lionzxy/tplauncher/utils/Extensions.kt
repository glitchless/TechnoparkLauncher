package ru.lionzxy.tplauncher.utils

import javafx.application.Platform
import javafx.event.EventTarget
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.apache.commons.codec.digest.DigestUtils
import tornadofx.*
import java.awt.Desktop
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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

fun Node.recursiveApplyToChild(op: Node.() -> Unit) {
    op()
    getChildList()?.forEach { it.recursiveApplyToChild(op) }
}

fun Node.recursiveDisable() {
    recursiveApplyToChild { addClass(Stylesheet.disabled) }
}

fun Node.recursiveEnable() {
    recursiveApplyToChild { removeClass(Stylesheet.disabled) }
}

fun File.hashSHA1(): String {
    return DigestUtils.sha1Hex(FileInputStream(this) as InputStream)
}

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * @param block a function to process this [Closeable] resource.
 * @return the result of [block] function invoked on this resource.
 */
public inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when {
            this == null -> {
            }
            exception == null -> close()
            else ->
                try {
                    close()
                } catch (closeException: Throwable) {
                    // cause.addSuppressed(closeException) // ignored here
                }
        }
    }
}