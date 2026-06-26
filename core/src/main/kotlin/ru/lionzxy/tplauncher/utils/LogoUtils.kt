package ru.lionzxy.tplauncher.utils

import com.google.gson.Gson
import ru.lionzxy.tplauncher.data.AssetsIndex
import ru.lionzxy.tplauncher.data.MinecraftAsset
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
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

    fun setLogoForMinecraft(minecraft: MinecraftContext) {
        val indexesFile = File(minecraft.getDirectory(), "assets/indexes/")
            .listFiles { _, name -> name.endsWith(".json") } ?: return
        val logo16x16 = getAsset(minecraft, ResourceHelper.getResource("icon/logo_16x16.png"))
        val logo32x32 = getAsset(minecraft, ResourceHelper.getResource("icon/logo_32x32.png"))
        indexesFile.forEach { pathAssetsFile(it, logo16x16, logo32x32) }
    }

    private fun pathAssetsFile(assetsFile: File, logo16x16: MinecraftAsset, logo32x32: MinecraftAsset) {
        val index = gson.fromJson(assetsFile.readText(), AssetsIndex::class.java)
        val newMap = HashMap(index.objects)
        newMap["icons/icon_16x16.png"] = logo16x16
        newMap["minecraft/icons/icon_16x16.png"] = logo16x16
        newMap["icons/icon_32x32.png"] = logo32x32
        newMap["minecraft/icons/icon_32x32.png"] = logo32x32
        index.objects = newMap
        assetsFile.writeText(gson.toJson(index))
    }

    private fun getAsset(minecraft: MinecraftContext, url: URL): MinecraftAsset {
        val tmpFile = File(ConfigHelper.getTemporaryDirectory(), "filetohash")
        tmpFile.delete()
        url.openStream().use { it.copyTo(tmpFile.outputStream()) }
        val hash = tmpFile.hashSHA1()
        val size = tmpFile.length()
        val target = File(minecraft.getDirectory(), "assets/objects/${hash.substring(0, 2)}/$hash")
        target.delete()
        tmpFile.copyTo(target)
        tmpFile.delete()
        return MinecraftAsset(hash, size.toInt())
    }
}
