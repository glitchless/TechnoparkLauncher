package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.OperatingSystem
import java.io.File

class WindowsPathFix(private val currentPath: String) : BaseWorkaround() {
    override fun processLaunchCommands(launchCommands: List<String>): List<String> {
        if (currentOS.type != OperatingSystem.WINDOWS) {
            return launchCommands
        }
        return launchCommands.map { replaceAbsolutePathInString(it) }
    }

    private fun replaceAbsolutePathInString(input: String): String {
        var pathForReplace = currentPath
        if (pathForReplace.last() != File.separatorChar) {
            pathForReplace += File.separatorChar
        }
        return input.replace(pathForReplace, ".${File.separatorChar}")
    }
}
