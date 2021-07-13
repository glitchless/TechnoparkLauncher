package ru.lionzxy.tplauncher.minecraft.delegates

import ru.lionzxy.tplauncher.minecraft.workarounds.BaseWorkaround
import ru.lionzxy.tplauncher.utils.Constants

object AuthDelegate : BaseWorkaround() {
    private val apiHost = Constants.USER_API_HOST

    override fun getAdditionalJavaArguments(): List<String> {
        return listOf(
            "-Dminecraft.api.auth.host" to apiHost,
            "-Dminecraft.api.account.host" to apiHost,
            "-Dminecraft.api.session.host" to apiHost,
            "-Dminecraft.api.services.host" to Constants.MINECRAFT_API_HOST
        ).map { "${it.first}=${it.second}" }
    }
}
