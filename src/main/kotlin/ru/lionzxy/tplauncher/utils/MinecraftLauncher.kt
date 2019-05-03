package ru.lionzxy.tplauncher.utils

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.downloader.DefaultLaunchSettings
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
    fun launch(minecraft: MinecraftInstance, session: ISession, java: File? = null): Boolean {
        val version = getVersion(minecraft)
        var runDetached = false
        val launchCommands =
            version.launcher.getLaunchCommand(
                session,
                minecraft,
                ServerInfo("minecraft.glitchless.ru", "Glitchless Server", null, 25565),
                version,
                DefaultLaunchSettings(java),
                null
            )

        val os = OperatingSystem.getOperatingSystem()
        if (os.isUnix) {
            runDetached = true
            launchCommands.add(0, "nohup")
        } else if (os.type == OperatingSystem.WINDOWS) {
            runDetached = true
            launchCommands.add(0, "start")
        }
        launchCommands.forEach { println(it) }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(minecraft.location)
        val proc = pb.start()
        //println("ProcId: ${proc.pid()}")
        return runDetached
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


