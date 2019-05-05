package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.impl.versions.mcdownload.MCDownloadVersionList
import java.io.File

object MinecraftLauncher {
    private var cacheVersion: IVersion? = null

    /**
     * @return runDetached if true run process on system support detach process
     */
    fun launch(minecraft: MinecraftInstance, session: ISession, java: File? = null) {
        val version = getVersion(minecraft)
        println("Minecraft Location: ${minecraft.location}")
        val launchCommands =
            version.launcher.getLaunchCommand(
                session,
                minecraft,
                ServerInfo("minecraft.glitchless.ru", "Glitchless Server", null, 25565),
                version,
                LauncherSettings(ConfigHelper.config.settings),
                null
            ).map {
                replaceAbsolutePathInString(it)
            }

        launchCommands.forEach { print("$it ") }
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS && ConfigHelper.config.settings.isDebug) {
            launchCommands.plus("&").plus("PAUSE")
        }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(minecraft.location)
        pb.start()
    }

    private val currentPathRelative =
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) ".\\" else "./"
    private val currentPathAbsolute = File("").absolutePath

    private fun replaceAbsolutePathInString(input: String): String {
        val postFix = if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) {
            '\\'
        } else {
            '/'
        }
        var pathForReplace = currentPathAbsolute
        if (pathForReplace.last() != postFix) {
            pathForReplace += postFix
        }
        return input.replace(pathForReplace, currentPathRelative)
    }

    fun getVersion(minecraftInstance: MinecraftInstance): IVersion {
        if (cacheVersion != null) {
            return cacheVersion!!
        }
        val versionList = MCDownloadVersionList(minecraftInstance)
        versionList.startDownload()
        cacheVersion = versionList.retrieveVersionInfo("1.12.2-forge1.12.2-14.23.5.2836")
        return cacheVersion!!
    }
}


