package ru.lionzxy.tplauncher.config

data class Config(
    var profile: Profile? = null,
    var downloadFirstPack: Boolean? = null,
    var lastUpdateFromChangeLog: Long? = null,
    var settings: Settings = Settings()
)