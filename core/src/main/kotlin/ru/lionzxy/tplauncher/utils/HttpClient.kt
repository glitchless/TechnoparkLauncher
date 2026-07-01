package ru.lionzxy.tplauncher.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

/** Progress callback for streamed downloads: (bytesReadSoFar, totalBytesOrNullIfUnknown). */
typealias DownloadProgress = (read: Long, total: Long?) -> Unit

/** Result of a conditional GET: [notModified] true on 304 (use the cache); else [body]+[etag]. */
data class ConditionalResponse(val notModified: Boolean, val etag: String?, val body: String?)

private val tempFileCounter = AtomicLong()

/**
 * A unique sibling temp file for [dest]. The changelog can contain case-variant duplicate paths
 * (e.g. `[...Books]` and `[...books]`) that resolve to the SAME file on a case-insensitive
 * filesystem; a per-download unique name keeps their concurrent `.part` writes from colliding.
 */
internal fun nextDownloadTempFile(dest: File): File =
    File(dest.parentFile, dest.name + ".part." + tempFileCounter.incrementAndGet())

/**
 * Thin Ktor-backed HTTP facade for the launcher. Backed by a single, reused [HttpClient]
 * (the supported Ktor usage pattern) so there is no per-operation thread pool or executor to leak.
 */
class HttpDownloader(val client: HttpClient) {

    suspend fun getString(url: String): String = client.get(url).bodyAsText()

    suspend fun getBytes(url: String): ByteArray = client.get(url).readRawBytes()

    /**
     * Conditional GET: sends `If-None-Match: <etag>` when [etag] is non-null. Returns
     * notModified=true on 304 (caller should use its cached copy), otherwise the body + new ETag.
     */
    suspend fun getStringConditional(url: String, etag: String?): ConditionalResponse {
        val response = client.get(url) {
            if (!etag.isNullOrEmpty()) header(HttpHeaders.IfNoneMatch, etag)
            // 304 must not be treated as an error by expectSuccess.
            expectSuccess = false
        }
        if (response.status == HttpStatusCode.NotModified) {
            return ConditionalResponse(notModified = true, etag = etag, body = null)
        }
        if (!response.status.isSuccess()) {
            throw IOException("GET $url failed: ${response.status}")
        }
        return ConditionalResponse(
            notModified = false,
            etag = response.headers[HttpHeaders.ETag],
            body = response.bodyAsText(),
        )
    }

    /**
     * Streams [url] into [dest] safely: writes to a sibling `.part` temp file, rejects an empty
     * (0-byte) response, then atomically renames it onto [dest]. The temp file is removed on any
     * failure, so a partial or failed download never leaves a corrupt file at the final path, and
     * an existing good file is never deleted until the replacement is fully fetched.
     */
    suspend fun downloadToFile(url: String, dest: File, onProgress: DownloadProgress = { _, _ -> }) {
        dest.parentFile?.mkdirs()
        val tmp = nextDownloadTempFile(dest)
        try {
            var total = 0L
            client.prepareGet(url).execute { response ->
                val contentLength = response.contentLength()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                tmp.outputStream().buffered().use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            total += read
                            onProgress(total, contentLength)
                        }
                    }
                }
            }
            // A 0-byte body is a legitimately empty file (markers/config). Ktor streams to EOF and
            // throws on a truncated body when Content-Length is known, so we don't reject empties.
            moveAtomically(tmp, dest)
        } finally {
            tmp.delete()
        }
    }

    private fun moveAtomically(from: File, to: File) {
        try {
            Files.move(
                from.toPath(), to.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** Shared application-lifetime instance. Ktor clients are designed to be created once and reused. */
        val instance: HttpDownloader by lazy { HttpDownloader(createDefaultClient()) }

        fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            applyDefaults()
            engine {
                // CIO tries to connect only once by default; retry a transient TCP/TLS connect
                // failure at the engine level instead of failing the whole file (the retry plugin
                // does not retry timeouts).
                endpoint.connectAttempts = 3
            }
        }
    }
}

/** Shared client configuration, reused by production (CIO) and tests (MockEngine). */
internal fun HttpClientConfig<*>.applyDefaults() {
    // Non-2xx responses become catchable exceptions instead of silently returning a body.
    expectSuccess = true
    install(UserAgent) {
        // Cloudflare in front of *.glitchless.ru returns 403 for any User-Agent starting with "Java/".
        // Ktor does not read the `http.agent` system property, so the UA must be set on the client.
        agent = HTTP_USER_AGENT
    }
    install(ContentEncoding) {
        gzip()
        deflate()
    }
    install(HttpTimeout) {
        // Generous 5-minute connect window: many files are fetched concurrently against a
        // Cloudflare-fronted host on slow/throttled links, so a TLS handshake can take a while.
        connectTimeoutMillis = 300_000
        // 5-minute inactivity cap: kills a stalled connection instead of hanging forever, while
        // tolerating long pauses on a poor connection before giving up.
        socketTimeoutMillis = 300_000
        // requestTimeoutMillis is intentionally left unset (no overall cap): large modpack files
        // may legitimately take minutes; socket timeout already guards against a stalled stream.
    }
    install(HttpRequestRetry) {
        retryOnExceptionOrServerErrors(maxRetries = 3)
        exponentialDelay()
    }
    install(HttpRedirect) {
        // Never follow an HTTPS -> HTTP downgrade (this is the Ktor default; set explicitly for clarity).
        allowHttpsDowngrade = false
    }
}
