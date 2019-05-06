package ru.lionzxy.tplauncher.view.main

import net.lingala.zip4j.core.ZipFile
import ru.lionzxy.tplauncher.config.Profile
import ru.lionzxy.tplauncher.downloader.Updater
import ru.lionzxy.tplauncher.minecraft.MinecraftLauncher
import ru.lionzxy.tplauncher.utils.*
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.impl.login.legacy.LegacyProfile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDLoginService
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import sk.tomsik68.mclauncher.util.FileUtils
import java.io.File

class MainController(val mainWindow: MainWindow) {
    var session: ISession? = null
    val minecraftInstance = MinecraftInstance(ConfigHelper.getDefaultDirectory())

    init {
        mainWindow.showProgress(false)
        mainWindow.showDownloadAndPlayButton(false)
        if (ConfigHelper.config.profile != null) {
            mainWindow.showLoginPassword(false)
            mainWindow.showDownloadAndPlayButton(true)
            session = ConfigHelper.config.profile
        }
    }

    fun onLogin(login: String, password: String) = runAsync {
        mainWindow.showProgress(true)
        mainWindow.setProgress(-1)
        mainWindow.showLoginPassword(false)
        if (login.isEmpty()) {
            mainWindow.setStatus(LocalizationHelper.getString("login_error_loginempty"))
            return@runAsync
        }

        try {
            session = YDLoginService().login(LegacyProfile(login, password))
        } catch (ex: YDServiceAuthenticationException) {
            ex.printStackTrace()
            mainWindow.showLoginPassword(true)
            mainWindow.showProgress(false)
            mainWindow.setStatus(LocalizationHelper.getString("login_invalid"))
            return@runAsync
        }
        ConfigHelper.writeToConfig {
            profile = Profile(session!!.username, session!!.sessionID, session!!.uuid)
        }

        downloadAndLaunch()
    }

    fun downloadAndLaunch() = runAsync {
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

        checkAndDownloadUpdate()

        runOnUi {
            mainWindow.close()
        }
        try {
            launch()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launch() {
        ConfigHelper.getDefaultDirectory().setWritableToFolder()
        MinecraftLauncher.launch(minecraftInstance, session!!)
        mainWindow.closeApplication()
    }

    private fun checkAndDownloadUpdate() {
        val updater = Updater()
        updater.initUpdater()
        updater.update(mainWindow)
    }

    private fun startDownloadZip() {
        val dist = File(ConfigHelper.getTemporaryDirectory(), "minecraft.zip")
        mainWindow.setStatus(LocalizationHelper.getString("download_mods"))
        FileUtils.downloadFileWithProgress(
            "https://minecraft.glitchless.ru/minecraft_dist/first_server/initial.zip",
            dist,
            mainWindow
        )
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