package ru.lionzxy.tplauncher.minecraft.workarounds

import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.io.File

object MacOSAppleSiliconLWJGLFix : BaseWorkaround() {
    override fun getAdditionalJavaArguments(): List<String> {
        val nativesFolder = File(ConfigHelper.getAppleSiliconWorkaroundDirectory(), "natives")
        return listOf("-Dorg.lwjgl.librarypath=${nativesFolder.absolutePath}")
    }

    override fun processLaunchCommands(launchCommands: List<String>): List<String> {
        return replaceAllLWJGLJars(launchCommands)
    }

    private fun replaceAllLWJGLJars(launchCommands: List<String>): List<String> {
        return launchCommands.map {
            if (it.contains("lwjgl") && !it.contains("-D")) {
                replaceLwjglLibs(it)
            } else it
        }
    }

    private fun replaceLwjglLibs(classpath: String): String {
        val withoutLwjgl = classpath.split(":").filter { !it.contains("lwjgl") }
        val lwihglJar = File(ConfigHelper.getAppleSiliconWorkaroundDirectory(), "lwjglfat.jar")
        return withoutLwjgl.plus(lwihglJar.absolutePath).joinToString(":")
    }
}
