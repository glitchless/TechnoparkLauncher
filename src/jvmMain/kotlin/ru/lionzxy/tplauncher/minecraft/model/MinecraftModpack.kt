package ru.lionzxy.tplauncher.minecraft.model


const val BASE_URL = "https://minecraft.glitchless.ru"

enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String?,
    val updateJsonLink: String?,
    val updateHostLink: String?,
    val defaultServer: ServerInfo?,
    val version: String
) {
    VANILLA(
        modpackName = "Vanilla",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/vanilla_changelog.json",
        updateHostLink = "$BASE_URL/incremental/vanilla",
        defaultServer = ServerInfo("mc.glitchless.ru", "Vanilla Server", 25566),
        version = "1.16.5-forge-36.0.0"
    ),
    GTNH(
        modpackName = "NewHorizon",
        initialDownloadLink = null,
        updateJsonLink = "$BASE_URL/incremental/gtnh_changelog.json",
        updateHostLink = "$BASE_URL/incremental/gtnh",
        defaultServer = null,
        version = "1.7.10-Forge10.13.4.1614-1.7.10"
    );

    override fun toString(): String {
        return modpackName
    }

    fun isOldVersion(): Boolean {
        return version.startsWith("1.12") || version.startsWith("1.7") //FIXME Dirty hack
    }
}