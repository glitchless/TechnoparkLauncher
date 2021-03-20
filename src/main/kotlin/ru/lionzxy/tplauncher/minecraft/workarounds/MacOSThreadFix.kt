package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.OperatingSystem

object MacOSThreadFix : BaseWorkaround() {
    override fun getAdditionalJavaArguments(): List<String> {
        if (currentOS.type == OperatingSystem.MACOS) {
            return listOf("-XstartOnFirstThread")
        }
        return emptyList()
    }
}
