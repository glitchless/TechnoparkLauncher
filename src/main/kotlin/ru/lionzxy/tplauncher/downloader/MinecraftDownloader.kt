package ru.lionzxy.tplauncher.downloader

import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.impl.versions.mcdownload.MCDownloadVersionList
import java.io.File


fun main(args: Array<String>) {
    val oldFile = File("/Users/nikita.kulikov/Library/Application Support/minecraft")
    val exist = File(
        "/Users/nikita.kulikov/Library/Application Support/minecraft/libraries",
        "com/mojang/text2speech/1.10.3/text2speech-1.10.3.jar"
    ).exists()
    val session = OfflineSession("LionZXY")
    val minecraft = MinecraftInstance(File("./minecraft"))
    val versionList = MCDownloadVersionList(minecraft)
    versionList.startDownload()
    val version = versionList.retrieveVersionInfo("1.12.2-forge1.12.2-14.23.5.2836")
    version.installer.install(version, minecraft, DefaultMonitor())

    val launchCommands =
        version.launcher.getLaunchCommand(session, minecraft, null, version, DefaultLaunchSettings(), null)
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

class DefaultMonitor : IProgressMonitor {
    var maxVal = 0;
    var currentProgress = 0
    override fun setProgress(progress: Int) {
        currentProgress = progress
        //println("$currentProgress/$maxVal")
    }

    override fun setStatus(status: String?) {
        println(status)
    }

    override fun incrementProgress(amount: Int) {
        currentProgress += amount
        //println("$currentProgress/$maxVal")
    }

    override fun setMax(len: Int) {
        maxVal = len
    }

}