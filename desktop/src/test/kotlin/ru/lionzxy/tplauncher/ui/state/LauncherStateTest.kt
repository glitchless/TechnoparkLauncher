package ru.lionzxy.tplauncher.ui.state

import org.junit.Assert.*
import org.junit.Test
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.theme.TpColors

class LauncherStateTest {

    // --- Initial ---

    @Test
    fun initialFlags_registerVisible() {
        val f = LauncherState.Initial.flags
        assertTrue("registerFieldIsVisible should be true", f.registerFieldIsVisible)
    }

    @Test
    fun initialFlags_progressText() {
        val f = LauncherState.Initial.flags
        assertEquals(Strings.enterLoginAndPassword, f.progressTextContent)
    }

    @Test
    fun initialFlags_defaults() {
        val f = LauncherState.Initial.flags
        assertFalse("loginPasswordVisible should be true", !f.loginPasswordVisible)
        assertFalse("successLoginVisible should be false", f.successLoginVisible)
        assertTrue("disableProgressBar should be true", f.disableProgressBar)
        assertFalse("buttonDisable should be false", f.buttonDisable)
        assertEquals(TpColors.accent, f.titleColor)
        assertEquals(Strings.enterGame, f.buttonText)
    }

    // --- InitialError ---

    @Test
    fun initialErrorFlags_errorColoring() {
        val f = LauncherState.InitialError("Ошибка!").flags
        assertEquals(TpColors.error, f.titleColor)
        assertEquals(TpColors.error, f.progressTextColor)
        assertEquals(TpColors.error, f.registerFieldColor)
    }

    @Test
    fun initialErrorFlags_buttonDisabledWithErrorText() {
        val f = LauncherState.InitialError("Боом").flags
        assertTrue("buttonDisable should be true", f.buttonDisable)
        assertEquals("Боом", f.buttonText)
        assertEquals("Боом", f.progressTextContent)
    }

    @Test
    fun initialErrorFlags_registerVisible() {
        val f = LauncherState.InitialError("err").flags
        assertTrue("registerFieldIsVisible should be true", f.registerFieldIsVisible)
    }

    // --- LoginProgress ---

    @Test
    fun loginProgressFlags_progressEnabled() {
        val f = LauncherState.LoginProgress.flags
        assertFalse("disableProgressBar should be false", f.disableProgressBar)
    }

    @Test
    fun loginProgressFlags_inputsDisabled() {
        val f = LauncherState.LoginProgress.flags
        assertTrue("disableInputField should be true", f.disableInputField)
        assertTrue("buttonDisable should be true", f.buttonDisable)
        assertTrue("disableSelectModpack should be true", f.disableSelectModpack)
    }

    @Test
    fun loginProgressFlags_progressTextNull() {
        val f = LauncherState.LoginProgress.flags
        assertNull("progressTextContent should be null", f.progressTextContent)
    }

    // --- Logged ---

    @Test
    fun loggedFlags_showsSuccessAndEmail() {
        val f = LauncherState.Logged("a@b.com").flags
        assertTrue("successLoginVisible should be true", f.successLoginVisible)
        assertFalse("loginPasswordVisible should be false", f.loginPasswordVisible)
        assertEquals("a@b.com", f.successLoginText)
    }

    @Test
    fun loggedFlags_goodGameText() {
        val f = LauncherState.Logged("a@b.com").flags
        assertEquals(Strings.goodGame, f.progressTextContent)
    }

    @Test
    fun loggedFlags_buttonEnabled() {
        val f = LauncherState.Logged("a@b.com").flags
        assertFalse("buttonDisable should be false", f.buttonDisable)
    }

    // --- GameLoading ---

    @Test
    fun gameLoadingFlags_progressEnabled() {
        val f = LauncherState.GameLoading("u@e.com").flags
        assertFalse("disableProgressBar should be false", f.disableProgressBar)
    }

    @Test
    fun gameLoadingFlags_inputsDisabled() {
        val f = LauncherState.GameLoading("u@e.com").flags
        assertTrue("disableInputField should be true", f.disableInputField)
        assertTrue("buttonDisable should be true", f.buttonDisable)
        assertTrue("disableSelectModpack should be true", f.disableSelectModpack)
    }

    @Test
    fun gameLoadingFlags_loadingText() {
        val f = LauncherState.GameLoading("u@e.com").flags
        assertEquals(Strings.loadingGame, f.progressTextContent)
        assertEquals("u@e.com", f.successLoginText)
    }

    // --- MinecraftRunning ---

    @Test
    fun minecraftRunningFlags_settingsNotClickable() {
        val f = LauncherState.MinecraftRunning("x@y.com").flags
        assertFalse("settingsFieldIsClickable should be false", f.settingsFieldIsClickable)
    }

    @Test
    fun minecraftRunningFlags_isOpen() {
        val f = LauncherState.MinecraftRunning("x@y.com").flags
        assertTrue("isOpen should be true", f.isOpen)
    }

    @Test
    fun minecraftRunningFlags_progressTextNull() {
        val f = LauncherState.MinecraftRunning("x@y.com").flags
        assertNull("progressTextContent should be null for MinecraftRunning", f.progressTextContent)
    }

    // --- MinecraftLaunched ---

    @Test
    fun minecraftLaunchedFlags_isOpenFalse() {
        val f = LauncherState.MinecraftLaunched("a@b").flags
        assertFalse("isOpen should be false", f.isOpen)
    }

    @Test
    fun minecraftLaunchedFlags_settingsNotClickable() {
        val f = LauncherState.MinecraftLaunched("a@b").flags
        assertFalse("settingsFieldIsClickable should be false", f.settingsFieldIsClickable)
    }

    // --- LaunchError ---

    @Test
    fun launchErrorFlags_buttonEnabled_forRetry() {
        val f = LauncherState.LaunchError("u@e.com", "Bad error").flags
        assertFalse("buttonDisable should be false (enabled for retry)", f.buttonDisable)
    }

    @Test
    fun launchErrorFlags_errorColoring() {
        val f = LauncherState.LaunchError("u@e.com", "crash").flags
        assertEquals(TpColors.error, f.titleColor)
        assertEquals(TpColors.error, f.progressTextColor)
        assertEquals("crash", f.progressTextContent)
    }

    @Test
    fun launchErrorFlags_showsEmailAndError() {
        val f = LauncherState.LaunchError("u@e.com", "crash").flags
        assertFalse("loginPasswordVisible should be false", f.loginPasswordVisible)
        assertTrue("successLoginVisible should be true", f.successLoginVisible)
        assertEquals("u@e.com", f.successLoginText)
    }

    @Test
    fun launchErrorFlags_settingsClickableAndModpackEnabled() {
        val f = LauncherState.LaunchError("u@e.com", "err").flags
        assertTrue("settingsFieldIsClickable should be true (default)", f.settingsFieldIsClickable)
        assertFalse("disableSelectModpack should be false (default)", f.disableSelectModpack)
    }

    // --- ProgressUiState ---

    @Test
    fun progressUiState_defaults() {
        val s = ProgressUiState()
        assertNull(s.status)
        assertEquals(0f, s.value, 0.001f)
    }

    @Test
    fun progressUiState_customValues() {
        val s = ProgressUiState(status = "Loading...", value = 0.75f)
        assertEquals("Loading...", s.status)
        assertEquals(0.75f, s.value, 0.001f)
    }
}
