package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.config.Profile
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.MinecraftModpack
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.impl.login.legacy.LegacyProfile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDLoginService
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException

class MinecraftAccountManager() {
    public val isLogged = isLoggedInternal()
    private var session: ISession? = ConfigHelper.config.profile
    val minecraftInstance = MinecraftInstance(ConfigHelper.getMinecraftDirectory(MinecraftModpack.MIDGARD))

    fun getEmail(): String {
        if (!isLogged) {
            throw RuntimeException("User not logged!")
        }
        return ConfigHelper.config.profile!!.email!!
    }

    @Throws(YDServiceAuthenticationException::class)
    fun login(email: String, password: String) {
        session = YDLoginService().login(LegacyProfile(email, password))
        ConfigHelper.writeToConfig {
            profile = Profile(session!!.username, session!!.sessionID, session!!.uuid, email)
        }
    }

    fun launch() {
        MinecraftLauncher.launch(minecraftInstance, session!!)
    }

    private fun isLoggedInternal(): Boolean {
        val profile = ConfigHelper.config.profile ?: return false
        return !profile.email.isNullOrEmpty()
                && !profile.login.isNullOrEmpty()
                && !profile.accessToken.isNullOrEmpty()
                && !profile.profileId.isNullOrEmpty()
    }

}