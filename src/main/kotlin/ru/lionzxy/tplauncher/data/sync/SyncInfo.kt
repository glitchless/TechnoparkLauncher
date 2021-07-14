package ru.lionzxy.tplauncher.data.sync

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.*

class SyncInfo(
    var hashSHA256: String,
    var lastSync: Long
)

@Throws(Exception::class)
fun File.generateSHA256(): String? {
    val buffer = ByteArray(8192)
    var count: Int = 0
    val digest = MessageDigest.getInstance("SHA-256")
    val bis = BufferedInputStream(FileInputStream(this))
    while (bis.read(buffer).also { count = it } > 0) {
        digest.update(buffer, 0, count)
    }
    bis.close()
    val hash = digest.digest()
    return String(Base64.getEncoder().encode(hash))
}
