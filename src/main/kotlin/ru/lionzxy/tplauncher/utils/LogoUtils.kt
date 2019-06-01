package ru.lionzxy.tplauncher.utils

import javafx.scene.image.Image
import javafx.stage.Stage
import nu.redpois0n.oslib.OperatingSystem
import java.awt.Toolkit

object LogoUtils {
    val logoFile = ConfigHelper.getLogoFile()
    val logoUrl = ResourceHelper.getResource("icon/logo.png")

    fun prepareLogo() {
        if (logoFile.exists()) {
            return
        }
        logoUrl.openStream().copyTo(logoFile.outputStream())
    }

    fun setLogo() {
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.MACOS) {
            setLogoForMac()
        }
    }

    fun setLogo(stage: Stage) {
        stage.icons.add(Image(ResourceHelper.getResource("icon/logo.png").openStream()))
    }

    fun getArgumentForSetLogo(): String {
        return "-Xdock:icon=${logoFile.absolutePath}"
    }

    private fun setLogoForMac() {
        val image = Toolkit.getDefaultToolkit().getImage(logoUrl)
        com.apple.eawt.Application.getApplication().dockIconImage = image
    }
}