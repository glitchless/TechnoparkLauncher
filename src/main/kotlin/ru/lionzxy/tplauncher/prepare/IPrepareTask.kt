package ru.lionzxy.tplauncher.prepare

import ru.lionzxy.tplauncher.minecraft.MinecraftContext

interface IPrepareTask {
    @Throws(Exception::class)
    fun prepareMinecraft(minecraft: MinecraftContext)
}
