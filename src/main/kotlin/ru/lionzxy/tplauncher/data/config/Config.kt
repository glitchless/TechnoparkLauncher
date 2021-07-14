package ru.lionzxy.tplauncher.data.config

import com.google.gson.annotations.SerializedName
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.config.SyncInfo
import ru.lionzxy.tplauncher.data.auth.Profile
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack


data class Config(
    @SerializedName("profile")
    var profile: Profile? = null,
    @SerializedName("settings")
    var settings: Settings = Settings(),
    @SerializedName("currentModpack")
    var currentModpack: MinecraftModpack = MinecraftModpack.VANILLA,
    @SerializedName("modpackDownloadedInfo")
    var modpackDownloadedInfo: HashMap<String, DownloadedInfo>
    = HashMap(MinecraftModpack.values().map { it.modpackName to DownloadedInfo() }.toMap()),
    @SerializedName("modpackSyncInfo")
    var modpackSyncInfo: HashMap<String, SyncInfo> = HashMap()
)
