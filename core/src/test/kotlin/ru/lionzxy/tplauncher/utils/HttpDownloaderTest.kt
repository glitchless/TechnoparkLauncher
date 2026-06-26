package ru.lionzxy.tplauncher.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HttpDownloaderTest {

    private fun downloaderReturning(
        status: HttpStatusCode,
        body: ByteArray,
        withContentLength: Boolean = true,
    ): HttpDownloader {
        val engine = MockEngine {
            val headers = if (withContentLength) {
                headersOf(HttpHeaders.ContentLength, body.size.toString())
            } else {
                headersOf()
            }
            respond(content = body, status = status, headers = headers)
        }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    private fun tempDir() = Files.createTempDirectory("httpdl").toFile()

    @Test
    fun downloadToFileWritesBodyAndLeavesNoTempFile() = runTest {
        val payload = "hello-mod-bytes".toByteArray()
        val dir = tempDir()
        val dest = File(dir, "mods/a.jar")

        downloaderReturning(HttpStatusCode.OK, payload).downloadToFile("https://h/a.jar", dest)

        assertArrayEquals(payload, dest.readBytes())
        assertTrue(
            "no temp file must remain after success",
            dest.parentFile.listFiles()!!.none { it.name.contains(".part") },
        )
    }

    @Test
    fun downloadToFileCreatesNestedParentDirectories() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4)
        val dir = tempDir()
        val dest = File(dir, "a/b/c/file.bin")

        downloaderReturning(HttpStatusCode.OK, payload).downloadToFile("https://h/x", dest)

        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun downloadToFileHandlesUnknownContentLength() = runTest {
        // The old fork loop wrote a 0-byte file when Content-Length was absent; Ktor streams to EOF.
        val payload = "chunked-body-without-content-length".toByteArray()
        val dir = tempDir()
        val dest = File(dir, "chunked.bin")

        downloaderReturning(HttpStatusCode.OK, payload, withContentLength = false)
            .downloadToFile("https://h/chunked", dest)

        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun downloadToFileWritesEmptyFileForEmptyResponse() = runTest {
        // A 0-byte 200 response is a legitimately empty file (marker/config files), not a failure.
        val dir = tempDir()
        val dest = File(dir, "marker")

        downloaderReturning(HttpStatusCode.OK, ByteArray(0)).downloadToFile("https://h/marker", dest)

        assertTrue("empty file must be created", dest.exists())
        assertEquals(0L, dest.length())
        assertTrue("no temp file must remain", dir.listFiles()!!.none { it.name.contains(".part") })
    }

    @Test
    fun downloadToFilePropagatesHttpErrorAndLeavesNoFile() = runTest {
        val dir = tempDir()
        val dest = File(dir, "missing.bin")

        var threw = false
        try {
            downloaderReturning(HttpStatusCode.NotFound, "nope".toByteArray())
                .downloadToFile("https://h/missing", dest)
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("404 must throw", threw)
        assertFalse(dest.exists())
        assertTrue("no temp file must remain", dir.listFiles()!!.none { it.name.contains(".part") })
    }

    @Test
    fun downloadToFileDoesNotClobberExistingFileOnFailure() = runTest {
        val dir = tempDir()
        val dest = File(dir, "keep.bin")
        val good = "previously-good".toByteArray()
        dest.writeBytes(good)

        try {
            downloaderReturning(HttpStatusCode.NotFound, "boom".toByteArray())
                .downloadToFile("https://h/keep", dest)
        } catch (e: Exception) {
            // expected
        }

        assertArrayEquals("existing good file must survive a failed download", good, dest.readBytes())
    }

    @Test
    fun getStringReturnsBody() = runTest {
        val json = """{"100":{"a.txt":"1"}}"""
        assertEquals(json, downloaderReturning(HttpStatusCode.OK, json.toByteArray()).getString("https://h/j"))
    }

    @Test
    fun getBytesReturnsBody() = runTest {
        val payload = byteArrayOf(9, 8, 7, 6, 5)
        assertArrayEquals(payload, downloaderReturning(HttpStatusCode.OK, payload).getBytes("https://h/b"))
    }

    @Test
    fun concurrentDownloadsToSameDestinationDoNotRace() = runBlocking {
        // Two case-variant changelog keys (e.g. [...Books] vs [...books]) resolve to the SAME file on a
        // case-insensitive filesystem. Downloading them in parallel must not corrupt each other's temp
        // file (the NoSuchFileException seen in the field) — each download needs a unique temp name.
        val payload = ByteArray(50_000) { (it % 251).toByte() }
        val dl = downloaderReturning(HttpStatusCode.OK, payload)
        val dir = tempDir()
        val dest = File(dir, "lang/zh_CN.lang")

        val failures = (1..16).map {
            async(Dispatchers.IO) {
                runCatching { dl.downloadToFile("https://h/zh_CN.lang", dest) }.exceptionOrNull()
            }
        }.awaitAll().filterNotNull()

        assertTrue("no concurrent download should fail: $failures", failures.isEmpty())
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun downloadTempFileNamesAreUnique() {
        val dir = tempDir()
        val dest = File(dir, "zh_CN.lang")
        val a = nextDownloadTempFile(dest)
        val b = nextDownloadTempFile(dest)
        assertNotEquals(a.path, b.path)
        // Must stay in the destination's directory so the final move is an atomic same-filesystem rename.
        assertEquals(dir.canonicalPath, a.parentFile.canonicalPath)
    }
}
