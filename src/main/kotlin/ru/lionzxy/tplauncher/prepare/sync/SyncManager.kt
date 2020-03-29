package ru.lionzxy.tplauncher.prepare.sync

import org.zeroturnaround.zip.FileSource
import org.zeroturnaround.zip.ZipUtil
import ru.lionzxy.tplauncher.config.SyncInfo
import ru.lionzxy.tplauncher.config.generateSHA256
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.IPrepareTask
import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


private val syncFileList = listOf(
    "config/XaeroWaypoints",
    "options.txt",
    "optionsof.txt",
    "servers.dat",
    "local/client"
)
private val ignoreList = listOf(".DS_Store")
private const val TMP_ZIP_FILE = "tmp_user_file.zip"

class SyncManager : IPrepareTask {

    override fun prepareMinecraft(minecraft: MinecraftContext) {
        val zipFile = zipUserFile(minecraft)
        var syncInfo = ConfigHelper.config.modpackSyncInfo[minecraft.modpack.modpackName]
        if (syncInfo == null) {
            syncInfo = SyncInfo("", 0)
        }

        if (syncFromServer(minecraft, syncInfo)) {
            val filename = SimpleDateFormat("yyyy-MM-dd-HH-mm").format(Date())
            zipFile.copyTo(File(ConfigHelper.getBackupFolder(), "${minecraft.modpack.modpackName}-$filename-sync.zip"))
            return
        }

        val sha256 = zipFile.generateSHA256()
        if (syncInfo.hashSHA256 == sha256) {
            return
        }

        uploadArchive(minecraft, zipFile)
    }

    private fun zipUserFile(minecraft: MinecraftContext): File {
        val tmpFile = File(ConfigHelper.getTemporaryDirectory(), TMP_ZIP_FILE)
        if (tmpFile.exists()) {
            tmpFile.delete()
        }

        val fileList = ArrayList<File>()
        syncFileList.map { File(minecraft.getDirectory().absolutePath, it) }
            .filter { it.exists() }
            .forEach {
                if (it.isDirectory) {
                    fileList.addAll(it.getFileListRecursive(ignoreList))
                } else {
                    fileList.add(it)
                }
            }
        val zipEntry = fileList.map { FileSource(it.toRelativeString(minecraft.getDirectory()), it) }
        ZipUtil.pack(zipEntry.toTypedArray(), tmpFile)

        return tmpFile
    }

    private fun syncFromServer(minecraft: MinecraftContext, syncInfo: SyncInfo): Boolean {
        return true
    }

    private fun uploadArchive(minecraft: MinecraftContext, zipFile: File) {

    }
}

private fun File.getFileListRecursive(ignoreFiles: List<String>): ArrayList<File> {
    if (!isDirectory) {
        throw RuntimeException("Is not directory")
    }

    val toExit = ArrayList<File>()
    val listFile = listFiles() ?: return toExit
    listFile.forEach {
        if (it.isDirectory) {
            toExit.addAll(it.getFileListRecursive(ignoreFiles))
        } else {
            if (!ignoreFiles.contains(it.name)) {
                toExit.add(it)
            }
        }
    }
    return toExit
}
