package ru.lionzxy.tplauncher.services.modpack

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack

interface MinecraftModpackService {
    fun getCurrentModpack(): MinecraftModpack

    fun setCurrentModpack(modpack: MinecraftModpack)
}
