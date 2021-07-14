package ru.lionzxy.tplauncher.services.auth

import ru.lionzxy.tplauncher.data.auth.Profile
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException

interface MinecraftAccountService {
    /**
     * Login via games.glitchless.ru server
     */
    @Throws(YDServiceAuthenticationException::class)
    fun login(email: String, password: String)

    /**
     * Check exist or not profile information
     */
    fun isLogged(): Boolean

    /**
     * Return active session (saved) or null
     */
    fun getActiveSession(): Profile?
}
