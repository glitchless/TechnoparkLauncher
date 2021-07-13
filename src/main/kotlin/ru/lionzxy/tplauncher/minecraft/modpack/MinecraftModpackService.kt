package ru.lionzxy.tplauncher.minecraft.modpack

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack

interface MinecraftModpackService {
    fun getCurrentModpack(): MinecraftModpack

    fun setCurrentModpack(modpack: MinecraftModpack)
}
