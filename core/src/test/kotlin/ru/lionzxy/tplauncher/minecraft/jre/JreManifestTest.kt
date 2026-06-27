package ru.lionzxy.tplauncher.minecraft.jre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.lionzxy.tplauncher.config.generateSHA256
import java.io.File

class JreManifestTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sampleJson = """
        [
          {"code":"jre21","files":[
            {"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u1","javaRelativePath":"jre-21/bin/java","SHA-256":"a","javaSHA-256":"ja"},
            {"type":"macOS","arch":"arm64","extension":"tar.gz","downloadUrl":"u2","javaRelativePath":"jre-21.jre/bin/java","SHA-256":"b","javaSHA-256":"jb"},
            {"type":"Windows","arch":"x86_64","extension":"zip","downloadUrl":"u3","javaRelativePath":"jre-21/bin/java.exe","SHA-256":"c","javaSHA-256":"jc"}
          ]},
          {"code":"jre8","files":[
            {"type":"macOS","arch":"x86_64","extension":"tar.gz","downloadUrl":"u4","javaRelativePath":"jdk8.jdk/bin/java","SHA-256":"d","javaSHA-256":"jd"}
          ]}
        ]
    """.trimIndent()

    @Test
    fun parsesAllEntriesAndMapsHashKeys() {
        val m = parseJreManifest(sampleJson)
        assertEquals(2, m.size)
        val jre21 = m.findByCode("jre21")!!
        assertEquals(3, jre21.files.size)
        val linux = jre21.files.first { it.type == "Linux" }
        assertEquals("a", linux.sha256)
        assertEquals("ja", linux.javaSha256)
        assertEquals("jre-21/bin/java", linux.javaRelativePath)
    }

    @Test
    fun findByCodeReturnsNullForUnknown() {
        assertNull(parseJreManifest(sampleJson).findByCode("jre99"))
    }

    @Test
    fun selectsMacArm64ForArmAliases() {
        val jre21 = parseJreManifest(sampleJson).findByCode("jre21")!!
        val f = jre21.selectFile("macOS", listOf("ARM", "arm64"))
        assertNotNull(f)
        assertEquals("u2", f!!.downloadUrl)
    }

    @Test
    fun selectsX64ForAmd64Aliases() {
        val jre21 = parseJreManifest(sampleJson).findByCode("jre21")!!
        val f = jre21.selectFile("Linux", listOf("x86_64", "amd64", "k8"))
        assertEquals("u1", f!!.downloadUrl)
    }

    @Test
    fun selectReturnsNullWhenNoPlatformMatch() {
        val jre8 = parseJreManifest(sampleJson).findByCode("jre8")!!
        assertNull(jre8.selectFile("Windows", listOf("x86_64")))     // jre8 has no Windows build here
        assertNull(jre8.selectFile("macOS", listOf("ARM", "arm64"))) // jre8 macOS is x86_64 only
    }

    @Test
    fun isJavaBinaryValidMatchesOwnHashAndRejectsMismatchOrMissing() {
        val dir = tmp.newFolder()
        val bin = File(dir, "java").apply { writeBytes("#!/bin/sh\necho 21".toByteArray()) }
        val good = bin.generateSHA256()!!
        assertTrue(isJavaBinaryValid(bin, good))
        assertFalse(isJavaBinaryValid(bin, "deadbeef="))
        assertFalse(isJavaBinaryValid(File(dir, "nope"), good))
    }
}
