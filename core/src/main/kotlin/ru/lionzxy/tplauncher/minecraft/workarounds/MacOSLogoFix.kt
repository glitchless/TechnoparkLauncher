package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.utils.ConfigHelper

object MacOSLogoFix : BaseWorkaround() {
    override fun getAdditionalJavaArguments(): List<String> {
        if (currentOS.type == OperatingSystem.MACOS) {
            return listOf("-Xdock:icon=${ConfigHelper.getLogoFile().absolutePath}")
        }
        return emptyList()
    }
}
