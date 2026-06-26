package ru.lionzxy.tplauncher.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.lionzxy.tplauncher.ui.state.LauncherState

/**
 * Deterministic unit tests for [LauncherViewModel].
 *
 * Covered paths (all synchronous, no network):
 *  - Invalid email (missing '@') → InitialError("Введите валидную почту")
 *  - Empty password → InitialError("Пароль не может быть пустым")
 *  - InitialError cleared on onPasswordOrLoginChange() → Initial
 *  - onButtonClick from Initial with invalid email → same InitialError
 *
 * NOT covered here (deferred to :core CLI integration + manual run):
 *  - onInitView(): reads real machine profile from ConfigHelper — non-deterministic
 *  - onLogin() success path: requires real network / mocked YDLoginService
 *  - onGameStart(): requires real network + process launch
 *  - delay(60_000) MinecraftRunning → MinecraftLaunched: real-time delay, no unit test
 *
 * The VM is constructed with Dispatchers.Unconfined so the synchronous validation
 * paths execute inline without needing a test dispatcher or TestCoroutineScope.
 * The async network launch in onLogin's success path is never reached for invalid inputs,
 * keeping these tests fast and free of real I/O.
 */
class LauncherViewModelTest {

    private lateinit var vm: LauncherViewModel

    @Before
    fun setUp() {
        vm = LauncherViewModel(CoroutineScope(Dispatchers.Unconfined))
    }

    // --- Invalid email validation ---

    @Test
    fun onLogin_invalidEmail_setsInitialError() {
        vm.onLogin("bademail", "somepassword")

        val state = vm.state.value
        assertTrue("Expected InitialError, got $state", state is LauncherState.InitialError)
        assertEquals(Strings.enterValidEmail, (state as LauncherState.InitialError).error)
    }

    // --- Empty password validation ---

    @Test
    fun onLogin_emptyPassword_setsInitialError() {
        vm.onLogin("a@b.com", "")

        val state = vm.state.value
        assertTrue("Expected InitialError, got $state", state is LauncherState.InitialError)
        assertEquals(Strings.passwordCannotBeEmpty, (state as LauncherState.InitialError).error)
    }

    // --- Reset from InitialError ---

    @Test
    fun onPasswordOrLoginChange_fromInitialError_resetsToInitial() {
        // Put VM into InitialError state first
        vm.onLogin("bademail", "x")
        assertTrue("Precondition: should be InitialError", vm.state.value is LauncherState.InitialError)

        vm.onPasswordOrLoginChange()

        assertEquals(LauncherState.Initial, vm.state.value)
    }

    @Test
    fun onPasswordOrLoginChange_fromInitial_noChange() {
        // Already in Initial; should stay Initial
        assertEquals(LauncherState.Initial, vm.state.value)

        vm.onPasswordOrLoginChange()

        assertEquals(LauncherState.Initial, vm.state.value)
    }

    // --- onButtonClick routing ---

    @Test
    fun onButtonClick_fromInitial_withInvalidEmail_setsInitialError() {
        // State starts as Initial
        assertEquals(LauncherState.Initial, vm.state.value)

        vm.onButtonClick("notanemail", "password")

        val state = vm.state.value
        assertTrue("Expected InitialError, got $state", state is LauncherState.InitialError)
        assertEquals(Strings.enterValidEmail, (state as LauncherState.InitialError).error)
    }

    @Test
    fun onButtonClick_fromInitialError_withInvalidEmail_setsInitialError() {
        // Seed an InitialError, then click again with still-bad input
        vm.onLogin("bademail", "x")
        assertTrue("Precondition: should be InitialError", vm.state.value is LauncherState.InitialError)

        vm.onButtonClick("stillbad", "x")

        val state = vm.state.value
        assertTrue("Expected InitialError, got $state", state is LauncherState.InitialError)
        assertEquals(Strings.enterValidEmail, (state as LauncherState.InitialError).error)
    }
}
