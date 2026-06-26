package ru.lionzxy.tplauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreMarkerTest {
    @Test
    fun marker_name_is_core() {
        assertEquals("core", CoreMarker.NAME)
    }
}
