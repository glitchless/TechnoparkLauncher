package ru.lionzxy.tplauncher.ui

import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier
import ru.lionzxy.tplauncher.minecraft.connectivity.VersionNotInstalledOfflineException
import ru.lionzxy.tplauncher.ui.state.LauncherState
import java.net.UnknownHostException

/** The UI state a launch/prepare failure maps to, plus whether it is worth reporting to Sentry. */
data class LaunchErrorMapping(val state: LauncherState, val reportToSentry: Boolean)

/**
 * Classifies a launch/prepare failure into a user-facing state. Environmental failures — a WSAEACCES
 * firewall/AV block, a not-installed-while-offline pack, plain no-network (unknown host, connection
 * refused/timed out, TLS refused) — are not bugs: they get actionable messages and are NOT reported
 * to Sentry. Everything else is an unexpected error worth reporting and shown as a generic internal
 * error.
 */
fun mapLaunchError(email: String, e: Throwable): LaunchErrorMapping = when {
    ConnectivityBlockClassifier.isPermissionDeniedSocket(e) ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.connectionBlocked), reportToSentry = false)

    // Checked before the generic environmental match: its cause chain usually contains the socket error.
    e is VersionNotInstalledOfflineException ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.notInstalledOffline), reportToSentry = false)

    e is UnknownHostException ->
        LaunchErrorMapping(LauncherState.InitialError(Strings.checkInternetConnection), reportToSentry = false)

    ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(e) ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.checkInternetConnection), reportToSentry = false)

    else ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.internalError), reportToSentry = true)
}
