package ru.lionzxy.tplauncher

import ru.lionzxy.tplauncher.log.Logger

/**
 * Static build/runtime facts about the launcher, sourced from the generated [BuildConfig].
 * Lives in :core so both the GUI (desktop `Main`) and the headless CLI ([MainCli]) can log
 * an identical startup banner as the first line of every session log.
 */
object AppInfo {
    val name: String get() = BuildConfig.NAME
    val version: String get() = BuildConfig.VERSION

    /** One-line summary for the top of a session log / support report. */
    val startupBanner: String
        get() = "$name v$version (jvm=${System.getProperty("java.version")}, " +
            "os=${System.getProperty("os.name")} ${System.getProperty("os.arch")})"

    /** Log [startupBanner] at INFO. Call once during process startup. */
    fun logStartup() = Logger.i("Launcher", startupBanner)
}
