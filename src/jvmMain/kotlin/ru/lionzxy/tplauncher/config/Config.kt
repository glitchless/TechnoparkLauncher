package ru.lionzxy.tplauncher.config

import kotlinx.serialization.Serializable
import ru.lionzxy.tplauncher.minecraft.model.MinecraftModpack


@Serializable
data class Config(
    var profile: Profile? = null,
    var settings: Settings = Settings(),
    var currentModpack: MinecraftModpack = MinecraftModpack.VANILLA,
    var modpackDownloadedInfo: HashMap<String, DownloadedInfo> =
        HashMap(MinecraftModpack.entries.map { it.modpackName to DownloadedInfo() }.toMap()),
    var modpackSyncInfo: HashMap<String, SyncInfo> = HashMap()
)
