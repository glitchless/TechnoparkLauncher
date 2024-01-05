package ru.lionzxy.tplauncher.utils

import java.awt.Desktop
import java.io.Closeable
import java.io.File
import java.lang.Long.signum
import java.lang.Math.abs
import java.net.URI
import java.text.CharacterIterator
import java.text.StringCharacterIterator

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

fun File.folderSize(): Long {
    if (isFile) {
        return length()
    }
    val files = listFiles()
    if (files == null || files.isEmpty()) {
        return 0
    }
    var length: Long = 0
    for (file in files) {
        length += if (file.isFile) file.length() else folderSize()
    }
    return length
}

fun Long.humanReadableByteCountBin(): String {
    val absB = if (this == Long.MIN_VALUE) Long.MAX_VALUE else abs(this)
    if (absB < 1024) {
        return "$this B"
    }
    var value = absB
    val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
    var i = 40
    while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
        value = value shr 10
        ci.next()
        i -= 10
    }
    value *= signum(this).toLong()
    return String.format("%.1f %ciB", value / 1024.0, ci.current())
}
