package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException

class InstallOfflineToleranceTest {
    private val offline = ConnectException("Connection refused: connect")
    private val blocked = SocketException("Permission denied: connect")

    @Test
    fun plainOfflineWithInstalledPackIsTolerated() {
        // e.g. mclauncher re-fetching the lwjgl natives artifact with the network down.
        assertTrue(shouldTolerateInstallFailure(offline, versionInstalledLocally = true, tolerateConnectivityBlock = false))
    }

    @Test
    fun notInstalledIsNeverTolerated() {
        assertFalse(shouldTolerateInstallFailure(offline, versionInstalledLocally = false, tolerateConnectivityBlock = false))
        assertFalse(shouldTolerateInstallFailure(blocked, versionInstalledLocally = false, tolerateConnectivityBlock = true))
    }

    @Test
    fun wsaeaccesSurfacesTheRepairAssistantOnFirstOccurrence() {
        // A firewall/AV block must fail the first launch so the guided repair panel appears.
        assertFalse(shouldTolerateInstallFailure(blocked, versionInstalledLocally = true, tolerateConnectivityBlock = false))
    }

    @Test
    fun wsaeaccesIsToleratedOnExplicitRetryFromThePanel() {
        assertTrue(shouldTolerateInstallFailure(blocked, versionInstalledLocally = true, tolerateConnectivityBlock = true))
    }

    @Test
    fun genuineErrorsAlwaysPropagate() {
        assertFalse(shouldTolerateInstallFailure(IOException("corrupt jar"), versionInstalledLocally = true, tolerateConnectivityBlock = true))
        assertFalse(shouldTolerateInstallFailure(IllegalStateException("bug"), versionInstalledLocally = true, tolerateConnectivityBlock = true))
    }
}
