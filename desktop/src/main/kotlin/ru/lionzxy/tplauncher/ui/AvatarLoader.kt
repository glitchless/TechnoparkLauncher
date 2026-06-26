package ru.lionzxy.tplauncher.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Image
import ru.lionzxy.tplauncher.data.AvatarResponse
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.UrlDownloader
import java.io.File

private val avatarMutex = Mutex()
private val gson = Gson()

/**
 * Loads the avatar for [login]:
 * 1. Returns cached `avatar.png` from the default directory if present.
 * 2. Otherwise fetches `https://games.glitchless.ru/api/minecraft/users/profiles/<login>/avatar/`,
 *    parses the JSON with Gson to get `avatar_url`, downloads the image, writes `avatar.png`, and
 *    returns the decoded bitmap.
 *
 * The function is serialized via a [Mutex] so concurrent callers never double-fetch.
 */
suspend fun loadAvatar(login: String): ImageBitmap? = avatarMutex.withLock {
    val cacheFile = File(ConfigHelper.getDefaultDirectory(), "avatar.png")
    if (cacheFile.exists()) {
        val bytes = cacheFile.readBytes()
        if (bytes.isNotEmpty()) {
            return@withLock Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    }

    return@withLock try {
        val apiUrl = "https://games.glitchless.ru/api/minecraft/users/profiles/$login/avatar/"
        val json = UrlDownloader.downloadToString(apiUrl)
        val response = gson.fromJson(json, AvatarResponse::class.java)
        val avatarUrl = response.data.avatarUrl

        val imageBytes = UrlDownloader.downloadBytes(avatarUrl).bodyBytes
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(imageBytes)

        Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
