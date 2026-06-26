package ru.lionzxy.tplauncher.snapshot

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.ui.settings.SettingsViewModel
import ru.lionzxy.tplauncher.ui.settings.SettingsWindowContent
import ru.lionzxy.tplauncher.ui.theme.TpTheme

class SettingsSnapshotTest {

    /**
     * Build a preview VM with Screen-5 values (no disk I/O via injected fakes).
     *
     * Screen-5 values:
     *   heap     = "3G"
     *   javaArgs = "-XX:+UseG1GC -XX:ConcGCThreads=1 -XX:ParallelGCThreads=1 -XX:MaxGCPauseMillis=50"
     *   prefix   = "cmd.exe /C start"
     *   javaPath = "C:\\Program Files\\Java\\java.exe"
     *   debug    = true (checked in mockup)
     *   autoJoin = false
     */
    private fun buildPreviewVm(): SettingsViewModel {
        val settings = Settings()
        settings.setHeapSize("3G")
        settings.customJavaParameter =
            "-XX:+UseG1GC -XX:ConcGCThreads=1 -XX:ParallelGCThreads=1 -XX:MaxGCPauseMillis=50"
        settings.commandPrefix = "cmd.exe /C start"
        settings.javaLocation = "C:\\Program Files\\Java\\java.exe"
        settings.isDebug = true
        settings.isAutoLoginMinecraft = false

        return SettingsViewModel(
            settings = settings,
            persist = { /* no-op in preview */ },
            onClose = { /* no-op in preview */ },
            onExitApp = { /* no-op in preview */ },
            backupSizeProvider = { "Очистить папку с бекапом (0 B)" }, // hermetic: no ConfigHelper
        )
    }

    @Test
    fun settingsSnapshot() {
        val previewVm = buildPreviewVm()
        val f = snapshot("settings", 960, 776) {
            TpTheme {
                SettingsWindowContent(previewVm)
            }
        }
        assertTrue("settings snapshot PNG must be non-empty", f.length() > 0)
    }
}
