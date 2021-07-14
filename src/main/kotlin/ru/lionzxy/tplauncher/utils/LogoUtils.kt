package ru.lionzxy.tplauncher.utils

import com.google.gson.Gson
import javafx.scene.image.Image
import javafx.stage.Stage
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.data.assets.AssetsIndex
import ru.lionzxy.tplauncher.data.assets.MinecraftAsset
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
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

    fun setLogo(stage: Stage) {
        if (OperatingSystem.getOperatingSystem().type == OperatingSystem.MACOS) {
            setLogoForMac()
        }
        stage.icons.add(Image(ResourceHelper.getResource("icon/logo.png").openStream()))
    }

    fun setLogoForMinecraft(modpack: MinecraftModpack) {
        val indexesFile = File(
            modpack.getDirectory(),
            "assets/indexes/"
        ).listFiles { _, name -> name.endsWith(".json") }
        val logo16x16 = getAsset(modpack, ResourceHelper.getResource("icon/logo_16x16.png"))
        val logo32x32 = getAsset(modpack, ResourceHelper.getResource("icon/logo_32x32.png"))
        indexesFile.forEach {
            pathAssetsFile(it, logo16x16, logo32x32)
        }
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

    private fun getAsset(modpack: MinecraftModpack, url: URL): MinecraftAsset {
        val tmpFile = File(ConfigHelper.getTemporaryDirectory(), "filetohash")
        tmpFile.delete()
        url.openStream().use { it.copyTo(tmpFile.outputStream()) }
        val hash = tmpFile.hashSHA1()
        val size = tmpFile.length()

        val target =
            File(modpack.getDirectory(), "assets/objects/${hash.substring(0, 2)}/$hash")
        target.delete()
        tmpFile.copyTo(target)
        tmpFile.delete()

        return MinecraftAsset(hash, size.toInt())
    }

    private fun setLogoForMac() {
        val image = Toolkit.getDefaultToolkit().getImage(logoUrl)
        // Only for JDK 8, for JDK 9 use https://stackoverflow.com/questions/6006173/how-do-you-change-the-dock-icon-of-a-java-program/56924202#56924202
        com.apple.eawt.Application.getApplication().dockIconImage = image
    }
}
