package ru.lionzxy.tplauncher.config

import com.google.gson.annotations.SerializedName
import ru.lionzxy.tplauncher.exceptions.HeapSizeInvalidException

/**
 * Mutable launch settings, persisted inside [Config] via Gson.
 *
 * The private backing fields keep the EXACT legacy JSON key names that the old
 * Java version wrote (`heapSize`, `customJavaParameter`, `commandPrefix`,
 * `autoLoginMinecraft`, `isDebug`) so configs already on disk
 * still deserialize. The public properties carry the lazy-default and validation
 * behavior the Java getters/setters had — including the quirky heap-size regex.
 * Old on-disk JSON containing `"javaLocation"` is silently ignored by Gson.
 */
class Settings() {

    @SerializedName("heapSize")
    private var heapSizeField: String? = null

    @SerializedName("customJavaParameter")
    private var customJavaParameterField: String? = null

    @SerializedName("commandPrefix")
    private var commandPrefixField: String? = null

    private var autoLoginMinecraft: Boolean = true

    var isDebug: Boolean = false

    /** Show the in-window log view on the main screen. Off by default; serialized as `enableLogView`. */
    var enableLogView: Boolean = false

    /** Copy constructor — mirrors the legacy `Settings(Settings)` (copies raw fields). */
    constructor(other: Settings) : this() {
        heapSizeField = other.heapSizeField
        customJavaParameterField = other.customJavaParameterField
        commandPrefixField = other.commandPrefixField
        isDebug = other.isDebug
        autoLoginMinecraft = other.autoLoginMinecraft
        enableLogView = other.enableLogView
    }

    var heapSize: String
        get() {
            if (heapSizeField == null) heapSizeField = SettingsDefault.getDefaultHeapSize()
            return heapSizeField!!
        }
        set(value) {
            if (!Regex("[0-9]*[G|g|M|m]").matches(value)) {
                throw HeapSizeInvalidException(value)
            }
            heapSizeField = value
        }

    var customJavaParameter: String
        get() {
            if (customJavaParameterField == null) {
                customJavaParameterField = SettingsDefault.getDefaultJavaArguments()
            }
            return customJavaParameterField!!
        }
        set(value) {
            customJavaParameterField = value
        }

    var commandPrefix: String
        get() {
            if (commandPrefixField == null) {
                commandPrefixField = SettingsDefault.getDefaultCommandPrefix()
            }
            return commandPrefixField!!
        }
        set(value) {
            commandPrefixField = value
        }

    var isAutoLoginMinecraft: Boolean
        get() = autoLoginMinecraft
        set(value) {
            autoLoginMinecraft = value
        }
}
