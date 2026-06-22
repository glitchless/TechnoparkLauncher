package ru.lionzxy.tplauncher.exceptions

import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException

/**
 * Thrown when the auth server rejected the login/password (HTTP 401/403). Extends
 * [YDServiceAuthenticationException] so the existing MainController login handler displays
 * [message] verbatim instead of the misleading "Mojang authentication service" text.
 */
class InvalidCredentialsException(thrown: Exception? = null) :
    YDServiceAuthenticationException("Неверный логин или пароль", thrown)
