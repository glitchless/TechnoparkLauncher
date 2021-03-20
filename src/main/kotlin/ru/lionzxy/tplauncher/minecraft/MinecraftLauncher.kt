package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.minecraft.delegates.AuthDelegate
import ru.lionzxy.tplauncher.minecraft.workarounds.*
import ru.lionzxy.tplauncher.utils.ConfigHelper
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.impl.versions.mcdownload.MCDownloadVersionList
import java.io.File
import java.net.UnknownHostException

class MinecraftLauncher(private val minecraft: MinecraftContext) {
    private var cacheVersion: IVersion? = null
    private val os = OperatingSystem.getOperatingSystem()
    private val workarounds: List<BaseWorkaround> = listOf(
        MacOSLogoFix,
        MacOSThreadFix,
        AuthDelegate,
        MacOSAppleSiliconLWJGLFix,
        WindowsPathFix(minecraft.getDirectory().absolutePath)
    )

    /**
     * @return runDetached if true run process on system support detach process
     */
    fun launch(session: ISession) {
        if (Thread.interrupted()) {
            throw InterruptedException()
        }
        val instance = minecraft.getMinecraftInstance()
        val version = getVersion()
        println("Minecraft Location: ${minecraft.getDirectory()}")

        val additionalJavaArguments =
            workarounds.map { it.getAdditionalJavaArguments() }.flatten()

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

        workarounds.forEach { workaround ->
            launchCommands = workaround.processLaunchCommands(launchCommands)
        }

        launchCommands.forEach { print("$it ") }
        if (os.type == OperatingSystem.WINDOWS && ConfigHelper.config.settings.isDebug) {
            launchCommands.plus("&").plus("PAUSE")
        }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(minecraft.getDirectory())
        pb.start()
    }

    fun getVersion(): IVersion {
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


