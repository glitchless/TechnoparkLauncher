package ru.lionzxy.tplauncher.minecraft.workarounds

import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.io.File

class MacOSAppleSiliconLWJGLFix(
    private val minecraftVersion: String
) : BaseWorkaround() {
    override fun getAdditionalJavaArguments(): List<String> {
        val nativesFolder = File(getLwjglFolder(), "natives")
        return listOf("-Dorg.lwjgl.librarypath=${nativesFolder.absolutePath}")
    }

    override fun processLaunchCommands(launchCommands: List<String>): List<String> {
        val jars = replaceAllLWJGLJars(launchCommands)
        return jars
    }

    private fun replaceAllLWJGLJars(launchCommands: List<String>): List<String> {
        return launchCommands.map {
            if (it.contains("lwjgl") && !it.contains("-D")) {
                replaceLwjglLibs(getLwjglFolder(), it)
            } else it
        }
    }

    private fun replaceLwjglLibs(lwjglFolder: File, classpath: String): String {
        val withoutLwjgl = classpath.split(":").filter { !it.contains("lwjgl") }
        val lwjglClasspath = File(lwjglFolder, "classpath")
            .walk()
            .asIterable()
            .toList()
            .filter { it.isFile && it.extension == "jar" }
        return withoutLwjgl.plus(lwjglClasspath).joinToString(":")
    }

    private fun getLwjglFolder(): File {
        //val isOldVersion = launchCommands.find { it.contains("lwjgl-2.9.4-nightly-20150209") } != null
        val isOldVersion = minecraftVersion.startsWith("1.12.") //FIXME Very dirty hack
        if (isOldVersion) {
            return File(ConfigHelper.getAppleSiliconWorkaroundDirectory(), "lwjgl2")
        }
        return File(ConfigHelper.getAppleSiliconWorkaroundDirectory(), "lwjgl3")
    }
}
