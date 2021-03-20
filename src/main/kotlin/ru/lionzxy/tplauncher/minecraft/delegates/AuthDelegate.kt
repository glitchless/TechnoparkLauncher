package ru.lionzxy.tplauncher.minecraft.delegates

import ru.lionzxy.tplauncher.minecraft.MINECRAFT_API_HOST
import ru.lionzxy.tplauncher.minecraft.USER_API_HOST
import ru.lionzxy.tplauncher.minecraft.workarounds.BaseWorkaround

object AuthDelegate : BaseWorkaround() {
    private val apiHost = USER_API_HOST

    override fun getAdditionalJavaArguments(): List<String> {
        return listOf(
            "-Dminecraft.api.auth.host" to apiHost,
            "-Dminecraft.api.account.host" to apiHost,
            "-Dminecraft.api.session.host" to apiHost,
            "-Dminecraft.api.services.host" to MINECRAFT_API_HOST
        ).map { "${it.first}=${it.second}" }
    }
}
