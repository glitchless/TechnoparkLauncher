package ru.lionzxy.tplauncher.minecraft

import org.junit.Assert.assertEquals
import org.junit.Test

class MinecraftModpackJreTest {

    @Test
    fun allCurrentModpacksDefaultToJre8() {
        MinecraftModpack.values().forEach {
            assertEquals("modpack ${it.modpackName} must default to jre8", "jre8", it.javaCode)
        }
    }

    @Test
    fun jresJsonLinkIsUnderBaseUrl() {
        assertEquals("https://minecraft.glitchless.ru/jres2.json", JRES_JSON_LINK)
    }
}
