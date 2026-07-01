package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.api.versions.IVersionInstaller
import sk.tomsik68.mclauncher.api.versions.IVersionLauncher
import java.io.IOException
import java.net.SocketException

private fun stubVersion(): IVersion = object : IVersion {
    override fun getDisplayName(): String = "test"
    override fun getId(): String = "1.7.10"
    override fun getUniqueID(): String = "r1.7.10"
    override fun getInstaller(): IVersionInstaller? = null
    override fun getLauncher(): IVersionLauncher? = null
    override fun isCompatible(): Boolean = true
    override fun getIncompatibilityReason(): String = ""
    override fun compareTo(other: IVersion): Int = 0
}

class OfflineVersionFallbackTest {
    @Test
    fun swallowsSocketExceptionAndReturnsLocalVersion() {
        val local = stubVersion()
        val result = resolveVersionWithOfflineFallback(
            versionId = "1.7.10",
            startDownload = { throw SocketException("Permission denied: connect") },
            retrieve = { local },
        )
        assertSame(local, result)
    }

    @Test
    fun throwsClearErrorWhenBlockedAndNotInstalled() {
        assertThrows(VersionNotInstalledOfflineException::class.java) {
            resolveVersionWithOfflineFallback(
                versionId = "1.7.10",
                startDownload = { throw SocketException("Permission denied: connect") },
                retrieve = { null },
            )
        }
    }

    @Test
    fun mapsRetrieveNetworkErrorToNotInstalledWhenOffline() {
        // With the version not on disk, mclauncher's unified list falls through to the online list,
        // which re-attempts the download and THROWS (it never returns null) — that too must map to
        // the clear "not installed offline" error instead of a raw socket exception.
        assertThrows(VersionNotInstalledOfflineException::class.java) {
            resolveVersionWithOfflineFallback(
                versionId = "1.7.10",
                startDownload = { throw SocketException("Permission denied: connect") },
                retrieve = { throw SocketException("Permission denied: connect") },
            )
        }
    }

    @Test
    fun swallowsManifestParseGarbageWhenInstalled() {
        // A captive portal / AV block page returns HTML instead of the manifest JSON; the parser
        // throws an unchecked exception, not an IOException.
        val local = stubVersion()
        val result = resolveVersionWithOfflineFallback(
            versionId = "1.7.10",
            startDownload = { throw ClassCastException("JSONArray cannot be cast to JSONObject") },
            retrieve = { local },
        )
        assertSame(local, result)
    }

    @Test
    fun rethrowsNonEnvironmentalManifestFailure() {
        // An HTTP 500 from the manifest host is a real failure worth surfacing (and reporting),
        // not an offline condition to silently paper over with a stale on-disk version.
        assertThrows(IOException::class.java) {
            resolveVersionWithOfflineFallback(
                versionId = "1.7.10",
                startDownload = { throw IOException("Server returned HTTP response code: 500") },
                retrieve = { stubVersion() },
            )
        }
    }

    @Test
    fun retrieveFailurePropagatesWhenManifestFetchWorked() {
        // If startDownload succeeded, a retrieve() failure is a genuine error, not "offline".
        assertThrows(SocketException::class.java) {
            resolveVersionWithOfflineFallback(
                versionId = "1.7.10",
                startDownload = {},
                retrieve = { throw SocketException("Connection reset") },
            )
        }
    }

    @Test
    fun happyPathReturnsResolvedVersion() {
        val v = stubVersion()
        assertSame(v, resolveVersionWithOfflineFallback("1.7.10", startDownload = {}, retrieve = { v }))
    }
}
