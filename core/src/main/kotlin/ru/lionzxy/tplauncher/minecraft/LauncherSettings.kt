package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.utils.WindowsPathHelper
import sk.tomsik68.mclauncher.api.common.ILaunchSettings
import java.io.File

class LauncherSettings(
    val settings: Settings,
    private val additionalJavaArguments: List<String> = listOf(),
    private val javaLocation: File? = null,
) : ILaunchSettings {
    override fun isModifyAppletOptions() = false

    override fun getCustomParameters() = mutableMapOf<String, String>()

    override fun getCommandPrefix(): MutableList<String> {
        val isWindows = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS
        // Strip the legacy "cmd.exe /C start" prefix on Windows even if it's still persisted in an
        // existing config: it routes the launch through cmd/start, which corrupts non-ASCII paths
        // (Cyrillic game dir / JRE) and hides the child's output. Any genuinely custom prefix is kept.
        val prefix = if (isWindows) {
            settings.commandPrefix.replace("cmd.exe /C start", "").trim()
        } else {
            settings.commandPrefix
        }
        return prefix.split(" ").filter { it.isNotEmpty() }.toMutableList()
    }

    override fun getJavaArguments(): MutableList<String> {
        return settings.customJavaParameter.split(" ")
            .plus(additionalJavaArguments).toMutableList()
    }

    override fun getJavaLocation(): File? {
        val javaFile = javaLocation ?: return null
        // On Windows, use the JRE's 8.3 short (ASCII) path when it contains non-ASCII characters, so
        // javaw.exe can load jvm.dll despite a Cyrillic install path (JDK-8195129). No-op otherwise.
        val isWindows = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS
        return if (isWindows && !WindowsPathHelper.isAscii(javaFile.absolutePath)) {
            WindowsPathHelper.toShortPath(javaFile)
        } else {
            javaFile
        }
    }

    override fun getInitHeap() = "256M"

    override fun getHeap() = settings.heapSize

}
