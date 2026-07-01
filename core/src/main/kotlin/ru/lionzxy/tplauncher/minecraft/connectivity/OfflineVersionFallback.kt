package ru.lionzxy.tplauncher.minecraft.connectivity

import ru.lionzxy.tplauncher.log.Logger
import sk.tomsik68.mclauncher.api.versions.IVersion
import java.io.IOException

/** Thrown when the version JSON is not on disk AND the online manifest fetch failed. */
class VersionNotInstalledOfflineException(versionId: String) : IOException(
    "Minecraft version '$versionId' is not installed and could not be fetched (network blocked). " +
        "Launch once with a working connection to install it.",
)

/**
 * Resolves a Minecraft version, tolerating a blocked/unreachable network.
 *
 * [startDownload] populates the online manifest but is best-effort: any [IOException]
 * (SocketException/WSAEACCES, SocketTimeoutException, ConnectException, UnknownHostException,
 * SSLException) is swallowed, because [retrieve] reads the version JSON from disk first (mclauncher-api's
 * unified version list is local-first). Throws [VersionNotInstalledOfflineException] when nothing is on
 * disk and the online fetch failed, instead of an opaque NPE from a `!!` on a null result.
 */
fun resolveVersionWithOfflineFallback(
    versionId: String,
    startDownload: () -> Unit,
    retrieve: () -> IVersion?,
): IVersion {
    try {
        startDownload()
    } catch (e: IOException) {
        Logger.w("Launcher", "Online version manifest unavailable; using on-disk version list", e)
    }
    return retrieve() ?: throw VersionNotInstalledOfflineException(versionId)
}
