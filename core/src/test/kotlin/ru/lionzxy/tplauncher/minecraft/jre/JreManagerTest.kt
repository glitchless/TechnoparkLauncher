package ru.lionzxy.tplauncher.minecraft.jre

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.lionzxy.tplauncher.config.generateSHA256
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.applyDefaults
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File
import java.nio.file.Files

class JreManagerTest {

    private object NoopMonitor : IProgressMonitor {
        override fun setMax(len: Int) {}
        override fun setProgress(progress: Int) {}
        override fun setStatus(status: String?) {}
        override fun incrementProgress(amount: Int) {}
    }

    private fun okDownloader(body: String): HttpDownloader {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.length.toString()))
        }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    @Test
    fun resolveJavaBinaryUsesCachedManifestWhenBinaryExists() {
        val tmp = Files.createTempDirectory("jremgr").toFile()
        val installDirFor = { code: String -> File(tmp, "jre/$code") }
        val bin = File(installDirFor("jre8"), "j/bin/java").apply { parentFile.mkdirs(); writeBytes("real-java".toByteArray()) }
        val sha = bin.generateSHA256()!!
        val cache = File(tmp, "jres2.json").apply {
            writeText("""[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u","javaRelativePath":"j/bin/java","SHA-256":"a","javaSHA-256":"$sha"}]}]""")
        }

        val mgr = JreManager(
            http = okDownloader("[]"),
            manifestUrl = "https://unused",
            manifestCacheFile = cache,
            installDirFor = installDirFor,
            platform = JrePlatform("Linux", listOf("x86_64", "amd64"), isWindows = false),
            extract = { _, _, _ -> },
        )

        assertEquals(bin.absolutePath, mgr.resolveJavaBinary("jre8")!!.absolutePath)
        assertNull(mgr.resolveJavaBinary("jre99"))
    }

    @Test
    fun ensureInstalledSkipsDownloadWhenBinaryHashMatches() {
        val tmp = Files.createTempDirectory("jremgr2").toFile()
        val installDirFor = { code: String -> File(tmp, "jre/$code") }
        val bin = File(installDirFor("jre8"), "j/bin/java").apply {
            parentFile.mkdirs(); writeBytes("real-java-binary".toByteArray())
        }
        val sha = bin.generateSHA256()!!
        val manifest =
            """[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"http://no","javaRelativePath":"j/bin/java","SHA-256":"arch","javaSHA-256":"$sha"}]}]"""

        val mgr = JreManager(
            http = okDownloader(manifest),
            manifestUrl = "https://h/jres2.json",
            manifestCacheFile = File(tmp, "cache.json"),
            installDirFor = installDirFor,
            platform = JrePlatform("Linux", listOf("x86_64"), isWindows = false),
            extract = { _, _, _ -> throw IllegalStateException("must not extract when already installed") },
        )

        assertEquals(bin.absolutePath, mgr.ensureInstalled("jre8", NoopMonitor).absolutePath)
    }

    @Test
    fun resolveJavaBinaryUsesInMemoryCacheAfterEnsureInstalled() {
        val tmp = Files.createTempDirectory("jremgr3").toFile()
        val installDirFor = { code: String -> File(tmp, "jre/$code") }
        val bin = File(installDirFor("jre8"), "j/bin/java").apply {
            parentFile.mkdirs(); writeBytes("real-java-binary".toByteArray())
        }
        val sha = bin.generateSHA256()!!
        val manifest =
            """[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"http://no","javaRelativePath":"j/bin/java","SHA-256":"arch","javaSHA-256":"$sha"}]}]"""
        val cacheFile = File(tmp, "cache.json")
        val mgr = JreManager(
            http = okDownloader(manifest),
            manifestUrl = "https://h/jres2.json",
            manifestCacheFile = cacheFile,
            installDirFor = installDirFor,
            platform = JrePlatform("Linux", listOf("x86_64"), isWindows = false),
            extract = { _, _, _ -> throw IllegalStateException("must not extract when already installed") },
        )

        mgr.ensureInstalled("jre8", NoopMonitor)
        cacheFile.delete() // prove resolve does not depend on the disk manifest after ensureInstalled

        assertEquals(bin.absolutePath, mgr.resolveJavaBinary("jre8")!!.absolutePath)
    }

    @Test
    fun ensureInstalledThrowsOnUnsupportedOs() {
        val tmp = Files.createTempDirectory("jremgr4").toFile()
        val mgr = JreManager(
            http = okDownloader("[]"),
            manifestUrl = "https://h/jres2.json",
            manifestCacheFile = File(tmp, "cache.json"),
            installDirFor = { code -> File(tmp, "jre/$code") },
            platform = JrePlatform(osName = null, archAliases = emptyList(), isWindows = false),
            extract = { _, _, _ -> },
        )
        try {
            mgr.ensureInstalled("jre8", NoopMonitor)
            org.junit.Assert.fail("expected IOException for unsupported OS")
        } catch (e: java.io.IOException) {
            // expected
        }
    }
}
