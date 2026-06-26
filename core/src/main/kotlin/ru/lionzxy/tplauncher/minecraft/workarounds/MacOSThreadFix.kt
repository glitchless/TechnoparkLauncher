package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack

class MacOSThreadFix(private val minecraftModpack: MinecraftModpack) : BaseWorkaround() {
    override fun getAdditionalJavaArguments(): List<String> {
        if (currentOS.type != OperatingSystem.MACOS) {
            return emptyList()
        }
        if (minecraftModpack.isOldVersion()) {
            return emptyList()
        }
        return listOf("-XstartOnFirstThread")
    }
}
