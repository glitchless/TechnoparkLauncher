package ru.lionzxy.tplauncher.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.exceptions.HeapSizeInvalidException
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6
import ru.lionzxy.tplauncher.utils.folderSize
import ru.lionzxy.tplauncher.utils.humanReadableByteCountBin
import ru.lionzxy.tplauncher.ui.Strings
import java.awt.Desktop

/**
 * ViewModel for the settings window.
 *
 * All side-effecting seams are injected so tests can stay hermetic (no disk I/O):
 *   - [settings]   the mutable Settings object to read from and write to
 *   - [persist]    called with the modified Settings to flush to disk
 *   - [onClose]    called to close the settings window
 *   - [onExitApp]  called to terminate the application (used by logout / wipe)
 *
 * ## The heap save-bug (replicated verbatim from the legacy JavaFX SettingsWindow)
 *
 * In [apply], heap validation via [Settings.setHeapSize] may throw
 * [HeapSizeInvalidException]. The exception is caught, [heapError] is set, but
 * execution does **NOT return** — it falls through to write the other fields,
 * call [persist], and call [onClose].  The window therefore closes even when the
 * heap value was invalid, and the invalid heap is silently skipped while every
 * other field is persisted.  This matches the original behavior exactly.
 */
class SettingsViewModel(
    private val settings: Settings,
    private val persist: (Settings) -> Unit,
    private val onClose: () -> Unit,
    private val onExitApp: () -> Unit = {},
    // Injected so tests/snapshots don't trigger ConfigHelper (its init block is destructive). Prod default reads the real size.
    private val backupSizeProvider: () -> String = ::computeBackupLabel,
) {
    // ---- Editable fields (Compose state) ------------------------------------

    var heap by mutableStateOf(settings.heapSize)
        private set

    var javaArgs by mutableStateOf(settings.customJavaParameter)
        private set

    var prefix by mutableStateOf(settings.commandPrefix)
        private set

    var javaPath by mutableStateOf(settings.javaLocation ?: "")
        private set

    var debug by mutableStateOf(settings.isDebug)
        private set

    var autoJoin by mutableStateOf(settings.isAutoLoginMinecraft)
        private set

    // ---- Heap error indicator -----------------------------------------------

    private val _heapError = MutableStateFlow<String?>(null)
    val heapError: StateFlow<String?> = _heapError.asStateFlow()

    // ---- Backup size label (refreshed on clearBackup) -----------------------

    var backupSizeLabel by mutableStateOf(backupSizeProvider())
        private set

    // ---- Field change callbacks --------------------------------------------

    fun onHeapChange(s: String) {
        heap = s
        _heapError.value = null     // live RAM-field listener: clear error on any change
    }

    fun onJavaArgsChange(s: String) { javaArgs = s }
    fun onPrefixChange(s: String)   { prefix = s }
    fun onJavaPathChange(s: String) { javaPath = s }
    fun onDebugChange(v: Boolean)   { debug = v }
    fun onAutoJoinChange(v: Boolean) { autoJoin = v }

    // ---- Primary actions ----------------------------------------------------

    /**
     * Apply settings.
     *
     * THE BUG: if heap is invalid, [heapError] is set but execution continues —
     * the other fields are written to [settings], [persist] is called, and
     * [onClose] is invoked.  The window closes on an invalid heap value.
     */
    fun apply() {
        try {
            settings.setHeapSize(heap)
        } catch (e: HeapSizeInvalidException) {
            _heapError.value = e.message
            // NO return — fall through intentionally (the save-bug)
        }
        settings.customJavaParameter = javaArgs
        settings.commandPrefix = prefix
        settings.javaLocation = javaPath.ifBlank { null }
        settings.isDebug = debug
        settings.isAutoLoginMinecraft = autoJoin

        persist(settings)
        onClose()
    }

    fun back() = onClose()

    // ---- Side-effecting actions (not unit-tested; call OS/disk) ------------

    /** Open the game directory in the native file manager. */
    fun openGameDir() {
        val dir = ConfigHelper.getDefaultDirectory()
        Desktop.getDesktop().open(dir)
    }

    /** Clear the profile and exit the application. */
    fun logout() {
        ConfigHelper.writeToConfig { profile = null }
        onExitApp()
    }

    /** Delete the backup folder and refresh [backupSizeLabel]. */
    fun clearBackup() {
        ConfigHelper.getBackupFolder().deleteDirectoryRecursionJava6()
        backupSizeLabel = backupSizeProvider()
    }

    /**
     * Delete the game directory (except jrepath.txt and the jre/ folder)
     * and exit the application.
     */
    fun wipe() {
        val defaultDir = ConfigHelper.getDefaultDirectory()
        defaultDir.listFiles()?.forEach { file ->
            if (file.absolutePath == ConfigHelper.getJREPathFile().absolutePath) return@forEach
            if (file.absolutePath == ConfigHelper.getJavaDirectory().absolutePath) return@forEach
            file.deleteDirectoryRecursionJava6()
        }
        onExitApp()
    }

    // ---- Helpers ------------------------------------------------------------

}

/** Top-level (no instance state) so it can be the [SettingsViewModel.backupSizeProvider] default. */
private fun computeBackupLabel(): String {
    val size = ConfigHelper.getBackupFolder().folderSize().humanReadableByteCountBin()
    return Strings.clearBackup(size ?: "0 B")
}
