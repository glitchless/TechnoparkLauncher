package ru.lionzxy.tplauncher.minecraft

import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.IOException

/**
 * mclauncher-api's `HttpUtils.doJSONAuthenticationPost` has its response-code check commented out,
 * so a 401/403 from the auth server makes `getInputStream()` throw a generic IOException (kept in
 * `thrown`, not the JVM cause) instead of exposing the server's `{error,errorMessage}` body. That
 * surfaces as the misleading "Failed to authenticate using Mojang authentication service." message.
 *
 * Detect that case so callers can show a clear "wrong login/password" message, while still letting
 * genuine network failures (timeout, unknown host, SSL) fall through to connection-error handling.
 *
 * NOTE: this treats any 401/403 as bad credentials, which assumes requests reach the app. The
 * Cloudflare WAF in front of the server also answers 403 (error 1010) when the User-Agent starts
 * with "Java/"; [ru.lionzxy.tplauncher.utils.configureHttpUserAgent] fixes that at startup, so a
 * 403 here is the app's own ERR_WRONG_PASSWORD.
 */
private val HTTP_AUTH_REJECTED = Regex("response code: (401|403)")

internal fun YDServiceAuthenticationException.isInvalidCredentials(): Boolean {
    val thrown = this.thrown
    if (thrown !is IOException) return false
    val message = thrown.message ?: return false
    return HTTP_AUTH_REJECTED.containsMatchIn(message)
}
