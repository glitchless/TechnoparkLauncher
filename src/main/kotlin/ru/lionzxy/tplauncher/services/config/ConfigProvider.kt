package ru.lionzxy.tplauncher.services.config

import ru.lionzxy.tplauncher.data.config.Config

interface ConfigProvider {
    val config: Config

    fun writeToConfig(op: (Config.() -> Unit))
}
