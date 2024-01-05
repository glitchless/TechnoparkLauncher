package ru.lionzxy.tplauncher.config

import kotlinx.serialization.Serializable
import ru.lionzxy.tplauncher.config.SettingsDefault.getDefaultCommandPrefix
import ru.lionzxy.tplauncher.config.SettingsDefault.getDefaultHeapSize
import ru.lionzxy.tplauncher.config.SettingsDefault.getDefaultJavaArguments
import ru.lionzxy.tplauncher.config.SettingsDefault.getDefaultJavaLocation


@Serializable
data class Settings(
    private var heapSize: String? = null,
    private var customJavaParameter: String? = null,
    private var commandPrefix: String? = null,
    private var javaLocation: String? = null,
    var isAutoLoginMinecraft: Boolean = true,
    var isDebug: Boolean = false,
) {
    fun getHeapSize(): String {
        return heapSize ?: getDefaultHeapSize().also {
            heapSize = it
        }
    }

    fun setHeapSize(heapSize: String?) {
        if (!Regex("[0-9]*[G|g|M|m]").matches(heapSize!!)) {
            throw HeapSizeInvalidException(heapSize)
        }
        this.heapSize = heapSize
    }

    fun getCustomJavaParameter(): String {
        if (customJavaParameter == null) {
            customJavaParameter = getDefaultJavaArguments()
        }
        return customJavaParameter!!
    }

    fun setCustomJavaParameter(customJavaParameter: String?) {
        this.customJavaParameter = customJavaParameter
    }

    fun getCommandPrefix(): String {
        if (commandPrefix == null) {
            commandPrefix = getDefaultCommandPrefix()
        }
        return commandPrefix!!
    }

    fun setCommandPrefix(commandPrefix: String?) {
        this.commandPrefix = commandPrefix
    }

    fun getJavaLocation(): String? {
        if (javaLocation == null) {
            javaLocation = getDefaultJavaLocation()
        }
        return javaLocation
    }

    fun setJavaLocation(javaLocation: String?) {
        this.javaLocation = javaLocation
    }
}
