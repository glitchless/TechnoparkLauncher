package ru.lionzxy.tplauncher.utils

import org.apache.commons.codec.digest.DigestUtils
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.Long.signum
import java.lang.Math.abs
import java.text.CharacterIterator
import java.text.StringCharacterIterator

fun File.setWritableToFolder() {
    if (isDirectory) {
        listFiles()?.forEach { it.setWritableToFolder() }
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
        listFiles()?.forEach { it.deleteDirectoryRecursionJava6() }
    }
    delete()
}

fun File.hashSHA1(): String {
    return DigestUtils.sha1Hex(FileInputStream(this) as InputStream)
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
        length += if (file.isFile) file.length() else file.folderSize()
    }
    return length
}

fun Long.humanReadableByteCountBin(): String? {
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

/**
 * Closes the resource after [block], swallowing a close-time exception if [block] already threw.
 */
inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when {
            this == null -> {}
            exception == null -> close()
            else -> try {
                close()
            } catch (closeException: Throwable) {
                // ignored
            }
        }
    }
}
