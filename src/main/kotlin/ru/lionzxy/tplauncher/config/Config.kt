package ru.lionzxy.tplauncher.config

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack


data class Config(
    var profile: Profile? = null,
    var settings: Settings = Settings(),
    var currentModpack: MinecraftModpack = MinecraftModpack.MIDGARD,
    var modpackDownloadedInfo: HashMap<String, DownloadedInfo>
    = HashMap(MinecraftModpack.values().map { it.modpackName to DownloadedInfo() }.toMap())
)
