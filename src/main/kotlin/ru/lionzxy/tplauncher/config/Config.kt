package ru.lionzxy.tplauncher.config

data class Config(
    var profile: Profile? = null,
    var lastUpdate: Long? = null,
    var lastUpdateFromChangeLog: Long? = null
)