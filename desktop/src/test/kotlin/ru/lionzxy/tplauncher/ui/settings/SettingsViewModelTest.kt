package ru.lionzxy.tplauncher.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.config.Settings

/**
 * Hermetic unit tests for [SettingsViewModel].
 *
 * All seams (Settings, persist, onClose, onExitApp) are injected as fakes —
 * no disk, no ConfigHelper, no real filesystem.
 *
 * Key TDD cases:
 *  (a) apply() with invalid heap "3GB" → heapError != null AND persist WAS called AND onClose WAS called
 *      (the save-bug: saves+closes despite invalid heap — NO early return in apply())
 *  (b) apply() with valid heap "3G" → heapError == null AND persist called with heap applied
 *  (c) onHeapChange clears a prior heapError
 */
class SettingsViewModelTest {

    /** Create a fresh Settings with a valid starting heap (avoids NPE in copy-ctor). */
    private fun freshSettings(): Settings {
        val s = Settings()
        s.heapSize = "1G"
        return s
    }

    // -------------------------------------------------------------------------
    // (a) THE BUG: apply() with invalid heap saves anyway + closes
    // -------------------------------------------------------------------------
    @Test
    fun apply_invalidHeap_setsHeapError_AND_stillPersistsAndCloses() {
        val captured = mutableListOf<Settings>()
        var closeCalled = false

        val vm = SettingsViewModel(
            settings = freshSettings(),
            persist = { s: Settings -> captured.add(s) },
            onClose = { closeCalled = true },
            backupSizeProvider = { "" },
        )

        // Set invalid heap in the VM state
        vm.onHeapChange("3GB")
        vm.apply()

        // heapError must be set (validation did fire)
        assertNotNull("heapError must be non-null after invalid heap", vm.heapError.value)

        // persist WAS called — that's the bug: it saves even though heap is invalid
        assertTrue("persist must be called even on invalid heap (the save-bug)", captured.isNotEmpty())

        // onClose WAS called — window closes even on invalid heap (the save-bug)
        assertTrue("onClose must be called even on invalid heap (the save-bug)", closeCalled)
    }

    // -------------------------------------------------------------------------
    // (b) apply() with valid heap — no error, persist called with heap applied
    // -------------------------------------------------------------------------
    @Test
    fun apply_validHeap_noError_persistsWithHeapApplied() {
        val captured = mutableListOf<Settings>()
        var closeCalled = false

        val vm = SettingsViewModel(
            settings = freshSettings(),
            persist = { s: Settings -> captured.add(s) },
            onClose = { closeCalled = true },
            backupSizeProvider = { "" },
        )

        vm.onHeapChange("3G")
        vm.onJavaArgsChange("-XX:+UseG1GC")
        vm.apply()

        // No error
        assertNull("heapError must be null for valid heap", vm.heapError.value)

        // Persist was called
        assertTrue("persist must be called on valid apply", captured.isNotEmpty())

        // The persisted settings has the new heap
        assertEquals("persisted heap must be '3G'", "3G", captured.first().heapSize)

        // Window closed
        assertTrue("onClose must be called on valid apply", closeCalled)
    }

    // -------------------------------------------------------------------------
    // (c) onHeapChange clears a prior heapError
    // -------------------------------------------------------------------------
    @Test
    fun onHeapChange_clearsPriorHeapError() {
        val vm = SettingsViewModel(
            settings = freshSettings(),
            persist = {},
            onClose = {},
            backupSizeProvider = { "" },
        )

        // Seed an error via apply with bad heap
        vm.onHeapChange("3GB")
        vm.apply()
        assertNotNull("Precondition: heapError must be set", vm.heapError.value)

        // Now type a new value — error must clear
        vm.onHeapChange("2G")

        assertNull("heapError must be cleared after onHeapChange", vm.heapError.value)
    }

    // -------------------------------------------------------------------------
    // enableLogView round-trips through the VM and is persisted by apply()
    // -------------------------------------------------------------------------
    @Test
    fun apply_persistsEnableLogView() {
        val captured = mutableListOf<Settings>()
        val vm = SettingsViewModel(
            settings = freshSettings(),
            persist = { s: Settings -> captured.add(s) },
            onClose = {},
            backupSizeProvider = { "" },
        )

        assertFalse("enableLogView must initialize from settings (false)", vm.enableLogView)
        vm.onEnableLogViewChange(true)
        assertTrue("toggling updates VM state", vm.enableLogView)

        vm.apply()
        assertTrue("apply must persist enableLogView", captured.first().enableLogView)
    }

    // -------------------------------------------------------------------------
    // back() calls onClose
    // -------------------------------------------------------------------------
    @Test
    fun back_callsOnClose() {
        var closeCalled = false
        val vm = SettingsViewModel(
            settings = freshSettings(),
            persist = {},
            onClose = { closeCalled = true },
            backupSizeProvider = { "" },
        )

        vm.back()

        assertTrue("back() must call onClose", closeCalled)
    }
}
