package ru.lionzxy.tplauncher.config

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack


data class Config(
    var profile: Profile? = null,
    var settings: Settings = Settings(),
    var currentModpack: MinecraftModpack = MinecraftModpack.MIDGARD,
    var modpackDownloadedInfo: Map<MinecraftModpack, DownloadedInfo>
    = MinecraftModpack.values().map { it to DownloadedInfo() }.toMap()
)
