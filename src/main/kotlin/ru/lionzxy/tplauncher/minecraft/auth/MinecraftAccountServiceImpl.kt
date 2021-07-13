package ru.lionzxy.tplauncher.minecraft.auth

import com.squareup.anvil.annotations.ContributesBinding
import io.sentry.Sentry
import ru.lionzxy.tplauncher.config.Profile
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.setUser
import sk.tomsik68.mclauncher.impl.login.legacy.LegacyProfile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDLoginService
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MinecraftAccountServiceImpl @Inject constructor() : MinecraftAccountService {
    init {
        Sentry.getContext().setUser(ConfigHelper.config.profile)
    }

    override fun getActiveSession(): Profile? {
        return ConfigHelper.config.profile
    }

    @Throws(YDServiceAuthenticationException::class)
    override fun login(email: String, password: String) {
        val session = YDLoginService.custom("${Constants.USER_API_HOST}/")
            .login(LegacyProfile(email, password))
        ConfigHelper.writeToConfig {
            profile = Profile(session.username, session.sessionID, session.uuid, email)
        }
        Sentry.getContext().setUser(ConfigHelper.config.profile)
    }

    override fun isLogged(): Boolean {
        val profile = ConfigHelper.config.profile ?: return false
        return !profile.email.isNullOrEmpty()
                && !profile.login.isNullOrEmpty()
                && !profile.accessToken.isNullOrEmpty()
                && !profile.profileId.isNullOrEmpty()
    }
}
