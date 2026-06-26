package ru.lionzxy.tplauncher.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HttpUserAgentTest {

    private var saved: String? = null

    @Before
    fun save() {
        saved = System.getProperty("http.agent")
    }

    @After
    fun restore() {
        if (saved == null) System.clearProperty("http.agent") else System.setProperty("http.agent", saved)
    }

    // HttpURLConnection sends "${http.agent} Java/<version>"; Cloudflare 403s UAs starting with
    // "Java/". A non-blank http.agent moves "Java/" off the front, so it must be set when unset.
    @Test
    fun `sets a non-blank http agent when none is configured`() {
        System.clearProperty("http.agent")

        configureHttpUserAgent()

        val agent = System.getProperty("http.agent")
        assertEquals("TechnoparkLauncher", agent)
    }

    @Test
    fun `does not override an explicitly configured http agent`() {
        System.setProperty("http.agent", "Custom/9")

        configureHttpUserAgent()

        assertEquals("Custom/9", System.getProperty("http.agent"))
    }
}
