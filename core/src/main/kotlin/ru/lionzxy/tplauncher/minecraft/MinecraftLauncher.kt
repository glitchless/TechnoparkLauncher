package ru.lionzxy.tplauncher.minecraft

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.minecraft.delegates.AuthDelegate
import ru.lionzxy.tplauncher.minecraft.jre.JreManager
import ru.lionzxy.tplauncher.minecraft.workarounds.*
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.WindowsPathHelper
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
        MacOSThreadFix(minecraft.modpack),
        AuthDelegate,
        MacOSAppleSiliconLWJGLFix(minecraft.modpack),
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
        Logger.i("Launcher", "Minecraft location: ${minecraft.getDirectory()}")

        val additionalJavaArguments =
            workarounds.map { it.getAdditionalJavaArguments() }.flatten()

        val loginServer = if (ConfigHelper.config.settings.isAutoLoginMinecraft) {
            minecraft.modpack.defaultServer
        } else null

        val javaFile = JreManager.instance.resolveJavaBinary(minecraft.modpack.javaCode)
            ?: throw IllegalStateException(
                "Managed JRE '${minecraft.modpack.javaCode}' is not installed"
            )

        var launchCommands =
            version.launcher.getLaunchCommand(
                session,
                instance,
                loginServer,
                version,
                LauncherSettings(
                    ConfigHelper.config.settings,
                    additionalJavaArguments,
                    javaFile
                ),
                null
            ).filter { !it.isNullOrEmpty() }

        workarounds.forEach { workaround ->
            launchCommands = workaround.processLaunchCommands(launchCommands)
        }

        Logger.i("Launcher", "Launch command: " + launchCommands.joinToString(" ") { it.replace(" ", "\\ ") })
        if (os.type == OperatingSystem.WINDOWS && ConfigHelper.config.settings.isDebug) {
            launchCommands.plus("&").plus("PAUSE")
        }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(resolveWorkingDirectory(minecraft.getDirectory()))
        pb.start()
    }

    // On Windows, run the game from the directory's 8.3 short (ASCII) path when the real path contains
    // non-ASCII characters (e.g. a Cyrillic username in %APPDATA%). The relative paths produced by
    // WindowsPathFix then resolve against an ASCII working directory, so JDK 8 can load LWJGL natives
    // without going through the lossy ANSI codepage (JDK-8195129). No-op for ASCII paths / non-Windows.
    private fun resolveWorkingDirectory(directory: File): File {
        if (os.type != OperatingSystem.WINDOWS) return directory
        val path = directory.absolutePath
        if (WindowsPathHelper.isAscii(path)) return directory
        val short = WindowsPathHelper.toShortPath(directory)
        if (WindowsPathHelper.isAscii(short.absolutePath)) return short
        if (!WindowsPathHelper.isRepresentableInSystemEncoding(path)) {
            Logger.w(
                "Launcher",
                "Launch directory '$path' contains characters that are not representable in " +
                    "the system encoding (${System.getProperty("sun.jnu.encoding")}) and has no 8.3 short " +
                    "path. The game may fail to load native libraries. Move the launcher data to an " +
                    "ASCII-only path (e.g. C:\\TechnoMine)."
            )
        }
        return directory
    }

    fun getVersion(): IVersion {
        if (cacheVersion != null) {
            return cacheVersion!!
        }
        val versionList = MCDownloadVersionList(minecraft.getMinecraftInstance())
        try {
            versionList.startDownload()
        } catch (ex: UnknownHostException) {
            Logger.w("Launcher", "Failed to fetch version list (no network?)", ex)
        }
        cacheVersion = versionList.retrieveVersionInfo(minecraft.modpack.version)
        return cacheVersion!!
    }
}


