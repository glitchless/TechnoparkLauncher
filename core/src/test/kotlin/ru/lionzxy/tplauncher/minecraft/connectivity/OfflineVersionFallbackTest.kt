package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.api.versions.IVersionInstaller
import sk.tomsik68.mclauncher.api.versions.IVersionLauncher
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
    fun happyPathReturnsResolvedVersion() {
        val v = stubVersion()
        assertSame(v, resolveVersionWithOfflineFallback("1.7.10", startDownload = {}, retrieve = { v }))
    }
}
