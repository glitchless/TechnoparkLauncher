package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.impl.versions.mcdownload.MCDownloadVersionList
import java.io.File
import java.net.UnknownHostException

object MinecraftLauncher {
    private var cacheVersion: IVersion? = null

    /**
     * @return runDetached if true run process on system support detach process
     */
    fun launch(minecraft: MinecraftContext, session: ISession) {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        val instance = minecraft.getMinecraftInstance()
        val version = getVersion(minecraft)
        println("Minecraft Location: ${minecraft.getDirectory()}")
        var launchCommands =
            version.launcher.getLaunchCommand(
                session,
                instance,
                minecraft.modpack.defaultServer,
                version,
                LauncherSettings(ConfigHelper.config.settings),
                null
            ).filter { !it.isNullOrEmpty() }

        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) {
            launchCommands = launchCommands.map {
                replaceAbsolutePathInString(it, minecraft.getDirectory().absolutePath)
            }
        }

        launchCommands.forEach { print("$it ") }
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS && ConfigHelper.config.settings.isDebug) {
            launchCommands.plus("&").plus("PAUSE")
        }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(minecraft.getDirectory())
        pb.start()
    }

    private val currentPathRelative =
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) ".\\" else "./"

    private fun replaceAbsolutePathInString(input: String, currentPath: String): String {
        val postFix = if (OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS) {
            '\\'
        } else {
            '/'
        }
        var pathForReplace = currentPath
        if (pathForReplace.last() != postFix) {
            pathForReplace += postFix
        }
        return input.replace(pathForReplace, currentPathRelative)
    }

    fun getVersion(minecraft: MinecraftContext): IVersion {
        if (cacheVersion != null) {
            return cacheVersion!!
        }
        val versionList = MCDownloadVersionList(minecraft.getMinecraftInstance())
        try {
            versionList.startDownload()
        } catch (ex: UnknownHostException) {
            ex.printStackTrace()
        }
        cacheVersion = versionList.retrieveVersionInfo("1.12.2-forge1.12.2-14.23.5.2836")
        return cacheVersion!!
    }
}


