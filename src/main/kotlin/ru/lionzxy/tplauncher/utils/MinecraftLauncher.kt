package ru.lionzxy.tplauncher.utils

import ru.lionzxy.tplauncher.downloader.DefaultLaunchSettings
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.api.servers.ServerInfo
import sk.tomsik68.mclauncher.api.versions.IVersion
import sk.tomsik68.mclauncher.impl.versions.mcdownload.MCDownloadVersionList
import java.io.File

object MinecraftLauncher {
    private var cacheVersion: IVersion? = null

    fun launch(minecraft: MinecraftInstance, session: ISession, java: File? = null) {
        val version = getVersion(minecraft)
        val launchCommands =
            version.launcher.getLaunchCommand(
                session,
                minecraft,
                ServerInfo("glitchless.ru", "Glitchless Server", null, 25565),
                version,
                DefaultLaunchSettings(java),
                null
            )
        launchCommands.forEach { println(it) }

        val pb = ProcessBuilder(launchCommands)
        pb.redirectError(File("mcerr.log"))
        pb.redirectOutput(File("mcout.log"))
        pb.directory(minecraft.location)
        val proc = pb.start()
        proc.inputStream.bufferedReader().use { br ->
            while (proc.isAlive) {
                br.readLine()?.let { println(it) }
            }
        }
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


