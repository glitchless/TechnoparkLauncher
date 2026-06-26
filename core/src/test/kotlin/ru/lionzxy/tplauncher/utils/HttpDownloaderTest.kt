package ru.lionzxy.tplauncher.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
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
        assertFalse("temp .part must be gone after success", File(dir, "mods/a.jar.part").exists())
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
    fun downloadToFileRejectsEmptyResponse() = runTest {
        val dir = tempDir()
        val dest = File(dir, "empty.bin")

        var threw = false
        try {
            downloaderReturning(HttpStatusCode.OK, ByteArray(0)).downloadToFile("https://h/empty", dest)
        } catch (e: IOException) {
            threw = true
        }

        assertTrue("empty download must throw", threw)
        assertFalse(dest.exists())
        assertFalse(File(dir, "empty.bin.part").exists())
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
        assertFalse(File(dir, "missing.bin.part").exists())
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
}
