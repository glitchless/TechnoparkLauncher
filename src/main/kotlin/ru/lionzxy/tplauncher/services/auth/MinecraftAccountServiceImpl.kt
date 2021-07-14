package ru.lionzxy.tplauncher.services.auth

import com.squareup.anvil.annotations.ContributesBinding
import io.sentry.Sentry
import ru.lionzxy.tplauncher.data.auth.Profile
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.services.config.ConfigProvider
import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.setUser
import sk.tomsik68.mclauncher.impl.login.legacy.LegacyProfile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDLoginService
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MinecraftAccountServiceImpl @Inject constructor(
    private val configProvider: ConfigProvider
) : MinecraftAccountService {
    init {
        Sentry.getContext().setUser(configProvider.config.profile)
    }

    override fun getActiveSession(): Profile? {
        return configProvider.config.profile
    }

    @Throws(YDServiceAuthenticationException::class)
    override fun login(email: String, password: String) {
        val session = YDLoginService.custom("${Constants.USER_API_HOST}/")
            .login(LegacyProfile(email, password))
        configProvider.writeToConfig {
            profile = Profile(session.username, session.sessionID, session.uuid, email)
        }
        Sentry.getContext().setUser(configProvider.config.profile)
    }

    override fun isLogged(): Boolean {
        val profile = getActiveSession() ?: return false
        return !profile.email.isNullOrEmpty()
                && !profile.login.isNullOrEmpty()
                && !profile.accessToken.isNullOrEmpty()
                && !profile.profileId.isNullOrEmpty()
    }
}
