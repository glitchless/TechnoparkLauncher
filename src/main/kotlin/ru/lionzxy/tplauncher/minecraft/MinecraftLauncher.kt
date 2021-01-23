package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
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

        var additionalJavaArguments = listOf(
            LogoUtils.getArgumentForSetLogo()
        )
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.MACOS) {
            additionalJavaArguments = additionalJavaArguments.plus("-XstartOnFirstThread")
        }

        additionalJavaArguments = additionalJavaArguments.plus(getAuthParams(USER_API_HOST))

        var launchCommands =
            version.launcher.getLaunchCommand(
                session,
                instance,
                minecraft.modpack.defaultServer,
                version,
                LauncherSettings(
                    ConfigHelper.config.settings,
                    additionalJavaArguments
                ),
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

    private fun getAuthParams(apiHost: String): List<String> {
        return listOf(
            "-Dminecraft.api.auth.host" to apiHost,
            "-Dminecraft.api.account.host" to apiHost,
            "-Dminecraft.api.session.host" to apiHost,
            "-Dminecraft.api.services.host" to MINECRAFT_API_HOST
        ).map { "${it.first}=${it.second}" }
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
        cacheVersion = versionList.retrieveVersionInfo(minecraft.modpack.version)
        return cacheVersion!!
    }
}


