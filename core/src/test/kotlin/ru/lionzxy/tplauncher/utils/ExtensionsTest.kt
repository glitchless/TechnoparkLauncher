package ru.lionzxy.tplauncher.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ExtensionsTest {
    @Test
    fun sha256HexMatchesKnownVector() {
        val f = Files.createTempFile("sha", ".bin").toFile()
        f.writeText("abc")
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            f.sha256Hex(),
        )
    }

    @Test
    fun sha256HexOfEmptyFile() {
        val f = Files.createTempFile("sha", ".bin").toFile()
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            f.sha256Hex(),
        )
    }
}
