package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.OperatingSystem
import java.io.File

class WindowsPathFix(private val currentPath: String) : BaseWorkaround() {
    override fun processLaunchCommands(launchCommands: List<String>): List<String> {
        if (currentOS.type != OperatingSystem.WINDOWS) {
            return launchCommands
        }
        return launchCommands.map { rewriteToken(it) }
    }

    // Keeps the (potentially non-ASCII) absolute game directory out of the command line. The child
    // process runs with its working directory set to the game dir, so relative paths resolve back to
    // the right place. Exposed for testing.
    internal fun rewriteToken(token: String): String {
        // The bare game-dir token (e.g. the value of --gameDir) equals currentPath with no trailing
        // separator, so replaceAbsolutePathInString's "currentPath + separator" needle never matches
        // it and it would otherwise stay absolute. Map it to the working directory (".").
        if (token == currentPath) {
            return "."
        }
        return replaceAbsolutePathInString(token)
    }

    private fun replaceAbsolutePathInString(input: String): String {
        var pathForReplace = currentPath
        if (pathForReplace.last() != File.separatorChar) {
            pathForReplace += File.separatorChar
        }
        return input.replace(pathForReplace, ".${File.separatorChar}")
    }
}
