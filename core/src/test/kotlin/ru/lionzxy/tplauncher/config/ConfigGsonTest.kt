package ru.lionzxy.tplauncher.config

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards that the persisted UI scale survives a Gson round-trip (it lives in profile.json). */
class ConfigGsonTest {

    @Test
    fun uiScaleRoundTrips() {
        val json = Gson().toJson(Config().apply { uiScale = 4f })
        assertTrue(json, json.contains("\"uiScale\""))
        assertEquals(4f, Gson().fromJson(json, Config::class.java).uiScale, 0.0001f)
    }

    @Test
    fun uiScaleDefaultsToOne() {
        // A legacy config with no uiScale key must default to 1.0 (not 0).
        val c = Gson().fromJson("""{"currentModpack":"VANILLA"}""", Config::class.java)
        assertEquals(1f, c.uiScale, 0.0001f)
    }
}
