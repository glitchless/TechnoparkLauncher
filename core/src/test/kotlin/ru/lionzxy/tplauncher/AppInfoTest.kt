package ru.lionzxy.tplauncher

import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {

    @Test
    fun startupBanner_containsNameAndVersion() {
        val banner = AppInfo.startupBanner
        assertTrue(banner, banner.contains(BuildConfig.NAME))
        assertTrue(banner, banner.contains("v${BuildConfig.VERSION}"))
    }

    @Test
    fun startupBanner_includesJvmAndOs() {
        val banner = AppInfo.startupBanner
        assertTrue(banner, banner.contains("jvm=${System.getProperty("java.version")}"))
        assertTrue(banner, banner.contains("os=${System.getProperty("os.name")}"))
        assertTrue(banner, banner.contains(System.getProperty("os.arch")))
    }

    @Test
    fun version_followsMajorMinorPatchScheme() {
        // Guards the MAJOR/MINOR build wiring: a broken project.version falls back to
        // Gradle's literal "unspecified", which is non-blank but not a real version.
        assertTrue(AppInfo.version, Regex("""^\d+\.\d+\.\d+$""").matches(AppInfo.version))
    }
}
