package ru.lionzxy.tplauncher.utils

import com.google.gson.Gson
import javafx.scene.image.Image
import javafx.stage.Stage
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.data.AssetsIndex
import ru.lionzxy.tplauncher.data.MinecraftAsset
import java.awt.Toolkit
import java.io.File
import java.net.URL

object LogoUtils {
    private val logoFile = ConfigHelper.getLogoFile()
    private val logoUrl = ResourceHelper.getResource("icon/logo.png")
    private val gson = Gson()

    fun prepareLogo() {
        if (logoFile.exists()) {
            return
        }
        logoUrl.openStream().use { it.copyTo(logoFile.outputStream()) }
    }

    fun setLogo() {
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.MACOS) {
            setLogoForMac()
        }
    }

    fun setLogo(stage: Stage) {
        stage.icons.add(Image(ResourceHelper.getResource("icon/logo.png").openStream()))
    }

    fun setLogoForMinecraft(minecraftModpack: MinecraftModpack) {
        val indexesFile = File(
            ConfigHelper.getMinecraftDirectory(minecraftModpack),
            "assets/indexes/"
        ).listFiles { _, name -> name.endsWith(".json") }
        val logo16x16 = getAsset(minecraftModpack, ResourceHelper.getResource("icon/logo_16x16.png"))
        val logo32x32 = getAsset(minecraftModpack, ResourceHelper.getResource("icon/logo_32x32.png"))
        indexesFile.forEach {
            pathAssetsFile(it, logo16x16, logo32x32)
        }
    }

    fun getArgumentForSetLogo(): String {
        if (OperatingSystem.getOperatingSystem().type != OperatingSystem.MACOS) {
            return ""
        }
        return "-Xdock:icon=${logoFile.absolutePath}"
    }

    private fun pathAssetsFile(assetsFile: File, logo16x16: MinecraftAsset, logo32x32: MinecraftAsset) {
        val index = gson.fromJson(assetsFile.readText(), AssetsIndex::class.java)
        val newMap = HashMap(index.objects)
        newMap["icons/icon_16x16.png"] = logo16x16
        newMap["minecraft/icons/icon_16x16.png"] = logo16x16
        newMap["icons/icon_32x32.png"] = logo32x32
        newMap["minecraft/icons/icon_32x32.png"] = logo32x32
        index.objects = newMap
        val json = gson.toJson(index)
        assetsFile.writeText(json)
    }

    private fun getAsset(minecraftModpack: MinecraftModpack, url: URL): MinecraftAsset {
        val tmpFile = File(ConfigHelper.getTemporaryDirectory(), "filetohash")
        tmpFile.delete()
        url.openStream().use { it.copyTo(tmpFile.outputStream()) }
        val hash = tmpFile.hashSHA1()
        val size = tmpFile.length()

        val target =
            File(ConfigHelper.getMinecraftDirectory(minecraftModpack), "assets/objects/${hash.substring(0, 2)}/$hash")
        target.delete()
        tmpFile.copyTo(target)
        tmpFile.delete()

        return MinecraftAsset(hash, size.toInt())
    }

    private fun setLogoForMac() {
        val image = Toolkit.getDefaultToolkit().getImage(logoUrl)
        com.apple.eawt.Application.getApplication().dockIconImage = image
    }
}