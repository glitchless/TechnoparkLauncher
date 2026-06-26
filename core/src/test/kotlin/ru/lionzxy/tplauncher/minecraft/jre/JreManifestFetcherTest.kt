package ru.lionzxy.tplauncher.minecraft.jre

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.applyDefaults
import java.io.File
import java.io.IOException
import java.nio.file.Files

class JreManifestFetcherTest {

    private val json =
        """[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u","javaRelativePath":"j/bin/java","SHA-256":"a","javaSHA-256":"ja"}]}]"""

    private fun okDownloader(body: String): HttpDownloader {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.length.toString()))
        }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    private fun offlineDownloader(): HttpDownloader {
        val engine = MockEngine { throw IOException("offline") }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    private fun tempFile(name: String) = File(Files.createTempDirectory("jrecache").toFile(), name)

    @Test
    fun onlineFetchParsesAndWritesCache() = runTest {
        val cache = tempFile("jres2.json")
        val m = fetchJreManifest(okDownloader(json), "https://h/jres2.json", cache)
        assertEquals("jre8", m.single().code)
        assertTrue("cache file must be written", cache.exists())
        assertEquals(json, cache.readText())
    }

    @Test
    fun offlineReadsFromCache() = runTest {
        val cache = tempFile("jres2.json").apply { writeText(json) }
        val m = fetchJreManifest(offlineDownloader(), "https://h/jres2.json", cache)
        assertEquals("jre8", m.single().code)
    }

    @Test
    fun offlineWithoutCacheThrows() = runTest {
        val cache = tempFile("missing.json") // does not exist
        try {
            fetchJreManifest(offlineDownloader(), "https://h/jres2.json", cache)
            fail("expected IOException when offline and no cache")
        } catch (e: IOException) {
            // expected
        }
    }
}
