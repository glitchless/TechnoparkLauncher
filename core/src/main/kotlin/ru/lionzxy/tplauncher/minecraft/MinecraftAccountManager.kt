package ru.lionzxy.tplauncher.minecraft

import ru.lionzxy.tplauncher.config.Profile
import ru.lionzxy.tplauncher.exceptions.InvalidCredentialsException
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.setSentryUser
import sk.tomsik68.mclauncher.api.common.mc.MinecraftInstance
import sk.tomsik68.mclauncher.api.login.ISession
import sk.tomsik68.mclauncher.impl.login.legacy.LegacyProfile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDLoginService
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException

const val MINECRAFT_API_HOST = "https://games.glitchless.ru/api/minecraft"
const val USER_API_HOST = "${MINECRAFT_API_HOST}/users"

class MinecraftAccountManager(minecraftModpack: MinecraftModpack) {
    public val isLogged = isLoggedInternal()
    internal var session: ISession? = ConfigHelper.config.profile
    val minecraftInstance = MinecraftInstance(ConfigHelper.getMinecraftDirectory(minecraftModpack))

    init {
        setSentryUser(ConfigHelper.config.profile)
    }

    fun getEmail(): String {
        if (!isLogged) {
            throw RuntimeException("User not logged!")
        }
        return ConfigHelper.config.profile!!.email!!
    }

    @Throws(YDServiceAuthenticationException::class)
    fun login(email: String, password: String) {
        try {
            session = YDLoginService.custom("$USER_API_HOST/")
                .login(LegacyProfile(email, password))
        } catch (exp: YDServiceAuthenticationException) {
            // mclauncher-api masks a 401/403 (wrong login/password) as a generic IOException.
            // Surface a clear message; let real network failures keep their original handling.
            if (exp.isInvalidCredentials()) {
                throw InvalidCredentialsException(exp)
            }
            throw exp
        }
        ConfigHelper.writeToConfig {
            profile = Profile(session!!.username, session!!.sessionID, session!!.uuid, email)
        }
        setSentryUser(ConfigHelper.config.profile)
    }

    private fun isLoggedInternal(): Boolean {
        val profile = ConfigHelper.config.profile ?: return false
        return !profile.email.isNullOrEmpty()
                && !profile.login.isNullOrEmpty()
                && !profile.accessToken.isNullOrEmpty()
                && !profile.profileId.isNullOrEmpty()
    }

}
