package ru.lionzxy.tplauncher.ui.state

import androidx.compose.ui.graphics.Color
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.theme.TpColors

/**
 * Sealed hierarchy of all launcher UI states.
 * Each state produces its own [StateFlags] via the [flags] extension property.
 */
sealed class LauncherState {
    object Initial : LauncherState()
    data class InitialError(val error: String) : LauncherState()
    object LoginProgress : LauncherState()
    data class Logged(val email: String) : LauncherState()
    data class GameLoading(val email: String) : LauncherState()
    data class MinecraftRunning(val email: String) : LauncherState()
    data class MinecraftLaunched(val email: String) : LauncherState()
    data class LaunchError(val email: String, val error: String) : LauncherState()

    /**
     * A WSAEACCES firewall/AV block (e.g. Dr.Web) stopped the launch. [message] is the guidance to
     * show; [canFirewallFix] is true only when the Windows Firewall auto-fix is worth attempting
     * (Defender / none detected), driving the primary button ("allow access" vs "retry").
     */
    data class ConnectivityBlocked(
        val email: String,
        val message: String,
        val canFirewallFix: Boolean,
    ) : LauncherState()
}

/**
 * 15-flag model derived from the current [LauncherState].
 * Defaults match [legacy-javafx-ui/.../BaseState.kt].
 */
data class StateFlags(
    val titleColor: Color = TpColors.accent,
    val loginPasswordVisible: Boolean = true,
    val successLoginVisible: Boolean = false,
    val disableProgressBar: Boolean = true,
    val disableInputField: Boolean = false,
    val progressTextColor: Color = TpColors.progressTrack,
    val progressTextContent: String? = null,
    val buttonDisable: Boolean = false,
    val buttonText: String = Strings.enterGame,
    val successLoginText: String = "example@example.com",
    val isOpen: Boolean = true,
    val registerFieldIsVisible: Boolean = false,
    val registerFieldColor: Color = TpColors.accent,
    val settingsFieldIsClickable: Boolean = true,
    val disableSelectModpack: Boolean = false,
)

/** Derives [StateFlags] from the receiver [LauncherState]. */
val LauncherState.flags: StateFlags
    get() = when (this) {
        is LauncherState.Initial -> StateFlags(
            progressTextContent = Strings.enterLoginAndPassword,
            registerFieldIsVisible = true,
        )

        is LauncherState.InitialError -> StateFlags(
            titleColor = TpColors.error,
            progressTextColor = TpColors.error,
            progressTextContent = error,
            buttonDisable = true,
            buttonText = error,
            registerFieldIsVisible = true,
            registerFieldColor = TpColors.error,
        )

        is LauncherState.LoginProgress -> StateFlags(
            disableProgressBar = false,
            disableInputField = true,
            progressTextContent = null,
            buttonDisable = true,
            disableSelectModpack = true,
        )

        is LauncherState.Logged -> StateFlags(
            loginPasswordVisible = false,
            successLoginVisible = true,
            successLoginText = email,
            progressTextContent = Strings.goodGame,
        )

        is LauncherState.GameLoading -> StateFlags(
            loginPasswordVisible = false,
            successLoginVisible = true,
            disableProgressBar = false,
            disableInputField = true,
            progressTextContent = Strings.loadingGame,
            buttonDisable = true,
            successLoginText = email,
            disableSelectModpack = true,
        )

        is LauncherState.MinecraftRunning -> StateFlags(
            loginPasswordVisible = false,
            successLoginVisible = true,
            disableProgressBar = false,
            disableInputField = true,
            progressTextContent = null,
            buttonDisable = true,
            successLoginText = email,
            settingsFieldIsClickable = false,
            disableSelectModpack = true,
        )

        is LauncherState.MinecraftLaunched -> StateFlags(
            loginPasswordVisible = false,
            successLoginVisible = true,
            disableProgressBar = false,
            disableInputField = true,
            progressTextContent = null,
            buttonDisable = true,
            successLoginText = email,
            isOpen = false,
            settingsFieldIsClickable = false,
            disableSelectModpack = true,
        )

        is LauncherState.LaunchError -> StateFlags(
            titleColor = TpColors.error,
            loginPasswordVisible = false,
            successLoginVisible = true,
            disableProgressBar = true,
            progressTextColor = TpColors.error,
            progressTextContent = error,
            buttonDisable = false,
            successLoginText = email,
        )

        is LauncherState.ConnectivityBlocked -> StateFlags(
            titleColor = TpColors.error,
            loginPasswordVisible = false,
            successLoginVisible = true,
            disableProgressBar = true,
            progressTextColor = TpColors.error,
            progressTextContent = message,
            buttonDisable = false,
            buttonText = if (canFirewallFix) Strings.allowAccess else Strings.retry,
            successLoginText = email,
        )
    }
