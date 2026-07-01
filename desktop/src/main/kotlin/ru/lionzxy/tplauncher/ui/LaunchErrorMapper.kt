package ru.lionzxy.tplauncher.ui

import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier
import ru.lionzxy.tplauncher.ui.state.LauncherState
import java.net.UnknownHostException

/** The UI state a launch/prepare failure maps to, plus whether it is worth reporting to Sentry. */
data class LaunchErrorMapping(val state: LauncherState, val reportToSentry: Boolean)

/**
 * Classifies a launch/prepare failure into a user-facing state. A WSAEACCES firewall/AV block and a
 * plain no-network are environmental (not bugs): they get actionable messages and are NOT reported to
 * Sentry. Everything else is an unexpected error worth reporting and shown as a generic internal error.
 */
fun mapLaunchError(email: String, e: Throwable): LaunchErrorMapping = when {
    ConnectivityBlockClassifier.isPermissionDeniedSocket(e) ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.connectionBlocked), reportToSentry = false)

    e is UnknownHostException ->
        LaunchErrorMapping(LauncherState.InitialError(Strings.checkInternetConnection), reportToSentry = false)

    else ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.internalError), reportToSentry = true)
}
