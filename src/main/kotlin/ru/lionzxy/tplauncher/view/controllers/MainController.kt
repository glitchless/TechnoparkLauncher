package ru.lionzxy.tplauncher.view.controllers

import net.lingala.zip4j.core.ZipFile
import ru.lionzxy.tplauncher.config.Profile
import ru.lionzxy.tplauncher.downloader.OfflineSession
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LocalizationHelper
import ru.lionzxy.tplauncher.utils.MinecraftLauncher
import ru.lionzxy.tplauncher.utils.runOnUi
import ru.lionzxy.tplauncher.view.MainWindow
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.util.FileUtils
import tornadofx.runAsync
import java.io.File

class MainController(val mainWindow: MainWindow) {
    var session: ISession? = null
    val minecraftInstance = MinecraftInstance(ConfigHelper.getDefaultDirectory())

    init {
        mainWindow.showProgress(false)
        mainWindow.showDownloadAndPlayButton(false)
        if (ConfigHelper.config.profile != null) {
            mainWindow.hideLoginPassword()
            mainWindow.showDownloadAndPlayButton(true)
            session = OfflineSession(ConfigHelper.config.profile!!.login)
        }
    }

    fun onLogin(login: String, password: String) {
        if (login.isEmpty()) {
            mainWindow.setStatus(LocalizationHelper.getString("login_error_loginempty", "Login can't be empty!"))
            return
        }

        ConfigHelper.writeToConfig {
            profile = Profile(login, "Now empty")
        }
        session = OfflineSession(login)
        mainWindow.hideLoginPassword()
        downloadAndLaunch()
    }

    fun downloadAndLaunch() = runAsync {
        var jrePathLocal = ConfigHelper.config.jrePath
        if (ConfigHelper.config.lastUpdate == null) {
            mainWindow.showProgress(true)
            mainWindow.showDownloadAndPlayButton(false)
            startDownloadZip()
            startDownloadMinecraft()
            ConfigHelper.writeToConfig {
                lastUpdate = System.currentTimeMillis()
            }
            mainWindow.showProgress(false)
        }
        runOnUi {
            mainWindow.close()
        }
        if (jrePathLocal != null) {
            MinecraftLauncher.launch(minecraftInstance, session!!, File(ConfigHelper.getJREPathFile().readText()))
        } else {
            MinecraftLauncher.launch(minecraftInstance, session!!)
        }
    }

    private fun startDownloadZip() {
        val dist = File(ConfigHelper.getTemporaryDirectory(), "minecraft.zip")
        mainWindow.setStatus(LocalizationHelper.getString("download_mods"))
        FileUtils.downloadFileWithProgress("http://download.glitchless.ru/0.0.1_minecraft.zip", dist, mainWindow)
        val zipFile = ZipFile(dist)
        mainWindow.setStatus(LocalizationHelper.getString("unzip_mods"))
        mainWindow.setProgress(-1)
        zipFile.extractAll(ConfigHelper.getDefaultDirectory().absolutePath)
    }

    private fun startDownloadMinecraft() {
        mainWindow.setStatus(LocalizationHelper.getString("download_minecraft"))
        val version = MinecraftLauncher.getVersion(minecraftInstance)
        version.installer.install(version, minecraftInstance, mainWindow)
    }
}