package ru.lionzxy.tplauncher.minecraft.connectivity

import ru.lionzxy.tplauncher.log.Logger
import sk.tomsik68.mclauncher.api.versions.IVersion
import java.io.IOException

/** Thrown when the version JSON is not on disk AND the online manifest fetch failed. */
class VersionNotInstalledOfflineException(versionId: String, cause: Throwable? = null) : IOException(
    "Minecraft version '$versionId' is not installed and could not be fetched (network blocked). " +
        "Launch once with a working connection to install it.",
    cause,
)

/**
 * Resolves a Minecraft version, tolerating a blocked/unreachable network.
 *
 * [startDownload] populates the online manifest but is best-effort for ENVIRONMENTAL failures
 * (SocketException/WSAEACCES, SocketTimeoutException, ConnectException, UnknownHostException,
 * SSLException — see [ConnectivityBlockClassifier.isEnvironmentalNetworkFailure]) and for parse
 * garbage (a captive portal / AV block page returns HTML that the manifest parser turns into a
 * ClassCastException/NPE): [retrieve] reads the version JSON from disk first (mclauncher-api's
 * unified version list is local-first), so an already-installed modpack still resolves. A
 * NON-environmental [IOException] (e.g. the manifest host answering HTTP 500) is rethrown — that
 * is a real failure worth surfacing, not an offline condition to silently paper over with a
 * possibly stale on-disk version.
 *
 * [retrieve] itself may re-attempt the online fetch when the version is NOT on disk (mclauncher's
 * unified list falls through to the online list, which retries the download and rethrows its
 * network error), so its failure after a failed [startDownload] means "not installed + offline" —
 * mapped to a clear [VersionNotInstalledOfflineException] instead of an opaque NPE or raw socket
 * error.
 */
fun resolveVersionWithOfflineFallback(
    versionId: String,
    startDownload: () -> Unit,
    retrieve: () -> IVersion?,
): IVersion {
    var offline: Throwable? = null
    try {
        startDownload()
    } catch (e: IOException) {
        if (!ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(e)) throw e
        Logger.w("Launcher", "Online version manifest unavailable; using on-disk version list", e)
        offline = e
    } catch (e: RuntimeException) {
        // Parse garbage: a captive portal / AV block page served instead of the manifest JSON.
        Logger.w("Launcher", "Online version manifest unparseable; using on-disk version list", e)
        offline = e
    }
    val version = try {
        retrieve()
    } catch (e: Exception) {
        // Only reachable when the local list misses and the online list re-fails; if the manifest
        // fetch worked, let the error propagate — it is not an offline condition.
        if (offline == null) throw e
        Logger.w("Launcher", "Version '$versionId' not resolvable offline", e)
        null
    }
    return version ?: throw VersionNotInstalledOfflineException(versionId, offline)
}
