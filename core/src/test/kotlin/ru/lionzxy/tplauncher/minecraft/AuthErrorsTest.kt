package ru.lionzxy.tplauncher.minecraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.exceptions.InvalidCredentialsException
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AuthErrorsTest {

    // MainController catches YDServiceAuthenticationException and shows `reason?.error ?: localizedMessage`.
    // InvalidCredentialsException must be a YDServiceAuthenticationException carrying a clear message.
    @Test
    fun `invalid credentials exception is an auth exception with a clear message`() {
        val e: Exception = InvalidCredentialsException()

        assertTrue(e is YDServiceAuthenticationException)
        assertEquals("Неверный логин или пароль", e.localizedMessage)
    }

    // mclauncher-api's HttpUtils swallows the auth server's 403/401 body and surfaces it as a
    // generic IOException stored in `thrown` (getInputStream() throws on 4xx). That must map to
    // "invalid credentials" so the UI can show a clear message instead of the misleading Mojang text.
    @Test
    fun `http 403 from auth server is treated as invalid credentials`() {
        val ioe = IOException(
            "Server returned HTTP response code: 403 for URL: " +
                "https://games.glitchless.ru/api/minecraft/users/authenticate"
        )
        val exp = YDServiceAuthenticationException(
            "Failed to authenticate using Mojang authentication service.", ioe
        )

        assertTrue(exp.isInvalidCredentials())
    }

    @Test
    fun `http 401 from auth server is treated as invalid credentials`() {
        val ioe = IOException("Server returned HTTP response code: 401 for URL: https://x/authenticate")
        val exp = YDServiceAuthenticationException("Failed...", ioe)

        assertTrue(exp.isInvalidCredentials())
    }

    // A genuine network failure must NOT be reported as a wrong password.
    @Test
    fun `read timeout is not treated as invalid credentials`() {
        val exp = YDServiceAuthenticationException("Failed...", SocketTimeoutException("Read timed out"))

        assertFalse(exp.isInvalidCredentials())
    }

    @Test
    fun `unknown host is not treated as invalid credentials`() {
        val exp = YDServiceAuthenticationException("Failed...", UnknownHostException("games.glitchless.ru"))

        assertFalse(exp.isInvalidCredentials())
    }
}
