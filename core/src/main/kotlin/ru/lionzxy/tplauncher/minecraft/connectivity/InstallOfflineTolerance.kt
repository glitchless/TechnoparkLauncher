package ru.lionzxy.tplauncher.minecraft.connectivity

/**
 * Decides whether a failure of the (network-hungry) `installer.install` step may be tolerated so an
 * already-installed pack still launches from its on-disk files. Runtime-verified need: mclauncher's
 * installer re-downloads the lwjgl natives artifact on every launch, so without this guard an
 * installed pack can never launch offline despite the getVersion fallback.
 *
 * Rules:
 * - The version must already be installed locally — a half-installed pack must keep failing loudly.
 * - Only environmental network failures qualify; genuine errors (HTTP 500, broken file) propagate.
 * - A WSAEACCES firewall/AV block is tolerated only on an explicit retry from the repair screen
 *   ([tolerateConnectivityBlock]): on first occurrence the launch must fail so the guided repair
 *   assistant is offered — silently launching offline would hide the fix from exactly the users
 *   who need it, and the game itself could not reach the server anyway.
 */
fun shouldTolerateInstallFailure(
    e: Throwable,
    versionInstalledLocally: Boolean,
    tolerateConnectivityBlock: Boolean,
): Boolean {
    if (!versionInstalledLocally) return false
    if (!ConnectivityBlockClassifier.isEnvironmentalNetworkFailure(e)) return false
    return tolerateConnectivityBlock || !ConnectivityBlockClassifier.isPermissionDeniedSocket(e)
}
