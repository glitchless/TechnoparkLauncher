package ru.lionzxy.tplauncher.utils

import nu.redpois0n.oslib.OperatingSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

class WindowsPathHelperTest {
    private val isWindows = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS

    // GetShortPathNameW is Windows-only; everywhere else the path must pass through untouched.
    @Test
    fun `toShortPath is a no-op off Windows`() {
        assumeFalse(isWindows)
        val file = File("/home/user/Конст/technomine")
        assertEquals(file, WindowsPathHelper.toShortPath(file))
    }

    @Test
    fun `isAscii distinguishes ascii from non-ascii`() {
        assertTrue(WindowsPathHelper.isAscii("C:\\Users\\John\\technomine"))
        assertFalse(WindowsPathHelper.isAscii("C:\\Users\\Конст\\technomine"))
    }

    @Test
    fun `ascii text is representable in the system encoding`() {
        assertTrue(WindowsPathHelper.isRepresentableInSystemEncoding("C:\\Users\\John\\technomine"))
    }
}
