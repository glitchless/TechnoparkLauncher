package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [assembleBinaryCandidates] is pure File composition (platform-agnostic), so these tests use
 * paths that compose deterministically on the test host. The Windows-specific path resolution lives
 * in WindowsBinaryResolver, which is verified on Windows.
 */
class BinaryCandidatesTest {
    @Test
    fun includesProcessImageAndJavaJavawFromBothDirs() {
        val processImage = File("/app/launcher")
        val javaHome = File("/app/rt")
        val bundled = File("/games/jre/bin")
        val out = assembleBinaryCandidates(processImage, javaHome, bundled) { true }
        assertTrue(out.contains(processImage))
        assertTrue(out.contains(File(File(javaHome, "bin"), "java.exe")))
        assertTrue(out.contains(File(File(javaHome, "bin"), "javaw.exe")))
        assertTrue(out.contains(File(bundled, "java.exe")))
        assertTrue(out.contains(File(bundled, "javaw.exe")))
    }

    @Test
    fun dropsNonExistentAndDedupes() {
        val out = assembleBinaryCandidates(
            processImage = null,
            javaHome = File("/rt"),
            bundledJreBinDir = File("/rt/bin"), // == File(javaHome, "bin"), so java/javaw collide
            exists = { it.name == "javaw.exe" }, // only javaw "exists"
        )
        assertEquals(listOf(File("/rt/bin/javaw.exe")), out)
    }

    @Test
    fun allNullInputsGiveEmpty() {
        assertEquals(emptyList<File>(), assembleBinaryCandidates(null, null, null) { true })
    }
}
