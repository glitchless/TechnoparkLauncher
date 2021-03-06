package ru.lionzxy.tplauncher.minecraft.workarounds

import nu.redpois0n.oslib.AbstractOperatingSystem
import nu.redpois0n.oslib.OperatingSystem

abstract class BaseWorkaround {
    val currentOS: AbstractOperatingSystem = OperatingSystem.getOperatingSystem()

    open fun getAdditionalJavaArguments() = listOf<String>()

    open fun processLaunchCommands(launchCommands: List<String>) = launchCommands
}
