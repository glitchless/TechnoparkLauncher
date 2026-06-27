package ru.lionzxy.tplauncher.config

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Java -> Kotlin port of [Settings]: the persisted JSON must keep the
 * EXACT legacy key names so configs written by the old Java version still load.
 */
class SettingsGsonTest {

    @Test
    fun serializesWithLegacyJsonKeys() {
        val s = Settings().apply {
            heapSize = "3G"
            customJavaParameter = "-Xmx3G"
            commandPrefix = "pfx"
            isDebug = true
            isAutoLoginMinecraft = false
        }
        val json = Gson().toJson(s)

        assertTrue(json, json.contains("\"heapSize\""))
        assertTrue(json, json.contains("\"customJavaParameter\""))
        assertTrue(json, json.contains("\"commandPrefix\""))
        assertTrue(json, json.contains("\"autoLoginMinecraft\""))
        assertTrue(json, json.contains("\"isDebug\""))
        // Private backing-field names must NOT leak into the JSON.
        assertFalse(json, json.contains("Field"))
    }

    @Test
    fun deserializesLegacyJson() {
        // The obsolete "javaLocation" key must be ignored without error on old on-disk configs.
        val legacy = """{"heapSize":"2G","customJavaParameter":"-Xss512k",""" +
            """"commandPrefix":"p","javaLocation":"/j","autoLoginMinecraft":false,"isDebug":true}"""
        val s = Gson().fromJson(legacy, Settings::class.java)

        assertEquals("2G", s.heapSize)
        assertEquals("-Xss512k", s.customJavaParameter)
        assertEquals("p", s.commandPrefix)
        assertFalse(s.isAutoLoginMinecraft)
        assertTrue(s.isDebug)
    }

    @Test
    fun copyConstructorPreservesValues() {
        val a = Settings().apply {
            heapSize = "5G"
            isDebug = true
            isAutoLoginMinecraft = false
            enableLogView = true
        }
        val b = Settings(a)
        assertEquals("5G", b.heapSize)
        assertTrue(b.isDebug)
        assertFalse(b.isAutoLoginMinecraft)
        assertTrue(b.enableLogView)
    }

    @Test
    fun enableLogView_defaultsFalse_andRoundTripsUnderItsOwnKey() {
        assertFalse("enableLogView must default to false", Settings().enableLogView)

        val json = Gson().toJson(Settings().apply { enableLogView = true })
        assertTrue(json, json.contains("\"enableLogView\""))

        val back = Gson().fromJson(json, Settings::class.java)
        assertTrue(back.enableLogView)
    }

    @Test
    fun enableLogView_absentFromLegacyJson_staysFalse() {
        val legacy = """{"heapSize":"2G","autoLoginMinecraft":true,"isDebug":false}"""
        assertFalse(Gson().fromJson(legacy, Settings::class.java).enableLogView)
    }

    @Test
    fun parallelDownloads_defaultsToClampedCoreCount_andRoundTrips() {
        val expected = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        assertEquals(expected, Settings().parallelDownloads)
        val json = Gson().toJson(Settings().apply { parallelDownloads = 5 })
        assertTrue(json, json.contains("\"parallelDownloads\""))
        assertEquals(5, Gson().fromJson(json, Settings::class.java).parallelDownloads)
    }

    @Test
    fun parallelDownloads_absentFromLegacyJson_usesDefault() {
        val expected = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        val legacy = """{"heapSize":"2G","isDebug":false}"""
        assertEquals(expected, Gson().fromJson(legacy, Settings::class.java).parallelDownloads)
    }

    @Test
    fun parallelDownloads_isClampedOnWrite() {
        assertEquals(32, Settings().apply { parallelDownloads = 9999 }.parallelDownloads)
        assertEquals(1, Settings().apply { parallelDownloads = 0 }.parallelDownloads)
    }
}
