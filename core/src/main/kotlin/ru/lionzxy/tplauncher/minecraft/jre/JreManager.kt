package ru.lionzxy.tplauncher.minecraft.jre

import io.sentry.Sentry
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.utils.HttpDownloader
import java.io.File
import java.io.IOException

internal const val JRE_LOG_TAG = "JreManager"

/**
 * Fetches and parses the JRE manifest. On success the raw JSON is written to [cacheFile] so a later
 * offline run can still resolve an already-installed JRE. On network failure the cached JSON is used.
 * Throws if neither the network nor the cache yields a manifest.
 */
internal suspend fun fetchJreManifest(
    http: HttpDownloader,
    url: String,
    cacheFile: File,
): List<JreManifestEntry> {
    val networkJson = runCatching { http.getString(url) }
        .onFailure { e ->
            Logger.w(JRE_LOG_TAG, "Failed to fetch JRE manifest from $url; falling back to cache", e)
            Sentry.captureException(e)
        }
        .getOrNull()

    if (networkJson != null) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(networkJson)
        }.onFailure { Logger.w(JRE_LOG_TAG, "Failed to write JRE manifest cache", it) }
        return parseJreManifest(networkJson)
    }

    if (cacheFile.exists()) {
        Logger.i(JRE_LOG_TAG, "Using cached JRE manifest at ${cacheFile.absolutePath}")
        return parseJreManifest(cacheFile.readText())
    }

    throw IOException("JRE manifest unavailable: network failed and no cache at ${cacheFile.absolutePath}")
}
