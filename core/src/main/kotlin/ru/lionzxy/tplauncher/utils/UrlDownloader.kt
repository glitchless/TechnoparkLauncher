package ru.lionzxy.tplauncher.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.IDN
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/**
 * Downloads all bytes from the given URL and returns the response body as String.
 *
 * JVM: 1.8
 * Desktop-friendly: uses HttpURLConnection only.
 *
 * Corner cases handled:
 * - connect/read timeouts (with clear exception type/message)
 * - redirects (301/302/303/307/308) with max limit
 * - gzip/deflate response bodies
 * - charset detection from Content-Type (+ UTF-8 BOM)
 * - error bodies for HTTP >= 400 (so you still get the server message)
 * - unknown content length (streaming)
 * - optional proxy
 */
object UrlDownloader {

    data class Options(
        val connectTimeoutMs: Int = 10_000,
        val readTimeoutMs: Int = 20_000,
        val maxBytes: Long = 10L * 1024 * 1024, // 10 MiB safety limit
        val maxRedirects: Int = 8,
        val userAgent: String = "UrlDownloader/1.0 (JVM; Kotlin)",
        val acceptEncoding: String = "gzip, deflate",
        val followRedirects: Boolean = true,
        val proxy: Proxy? = null
    ) {
        init {
            require(connectTimeoutMs >= 0) { "connectTimeoutMs must be >= 0" }
            require(readTimeoutMs >= 0) { "readTimeoutMs must be >= 0" }
            require(maxBytes > 0) { "maxBytes must be > 0" }
            require(maxRedirects >= 0) { "maxRedirects must be >= 0" }
        }
    }

    class HttpStatusException(
        val code: Int,
        val url: String,
        val responseBody: String?,
        message: String
    ) : IOException(message)

    /**
     * Main function: download bytes and decode to string.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadToString(url: String, options: Options = Options()): String {
        val finalUrl = normalizeUrl(url)
        val response = downloadBytes(finalUrl, options)
        val charset = response.detectCharset() ?: StandardCharsets.UTF_8
        return try {
            String(response.bodyBytes, charset)
        } catch (e: Exception) {
            // If declared charset fails, fallback to UTF-8.
            String(response.bodyBytes, StandardCharsets.UTF_8)
        }
    }

    data class Response(
        val finalUrl: String,
        val code: Int,
        val headers: Map<String, List<String>>,
        val bodyBytes: ByteArray
    ) {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

        fun detectCharset(): Charset? {
            // 1) BOM (UTF-8) check
            if (bodyBytes.size >= 3 &&
                bodyBytes[0] == 0xEF.toByte() &&
                bodyBytes[1] == 0xBB.toByte() &&
                bodyBytes[2] == 0xBF.toByte()
            ) return StandardCharsets.UTF_8

            // 2) Content-Type charset=
            val ct = header("Content-Type") ?: return null
            val charset = parseCharsetFromContentType(ct) ?: return null
            return try {
                Charset.forName(charset)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Downloads and returns raw bytes + response metadata.
     * Throws HttpStatusException on HTTP >= 400 (includes parsed body string when possible).
     */
    @JvmStatic
    @Throws(IOException::class)
    fun downloadBytes(url: String, options: Options = Options()): Response {
        var currentUrl = normalizeUrl(url)
        var redirects = 0

        while (true) {
            val conn = openConnection(currentUrl, options)

            try {
                val code = conn.responseCode

                if (options.followRedirects && isRedirect(code)) {
                    if (redirects >= options.maxRedirects) {
                        throw IOException("Too many redirects (>${options.maxRedirects}) for URL: $url")
                    }
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect ($code) without Location header for URL: $currentUrl")

                    val next = resolveRedirect(currentUrl, location)
                    redirects++
                    currentUrl = next
                    continue
                }

                val headers = conn.headerFields.filterKeys { it != null }
                val bodyStream = when {
                    code >= 400 -> conn.errorStream ?: conn.inputStream
                    else -> conn.inputStream
                }

                val encoding = (conn.getHeaderField("Content-Encoding") ?: "").trim().toLowerCase()
                val decodedStream = decodeStream(bodyStream, encoding)

                val bytes = readAllBytesLimited(decodedStream, options.maxBytes)

                val response = Response(
                    finalUrl = currentUrl,
                    code = code,
                    headers = headers,
                    bodyBytes = bytes
                )

                if (code >= 400) {
                    // Try to decode body for diagnostics
                    val charset = response.detectCharset() ?: StandardCharsets.UTF_8
                    val bodyStr = runCatching { String(bytes, charset) }.getOrNull()
                    throw HttpStatusException(
                        code = code,
                        url = currentUrl,
                        responseBody = bodyStr,
                        message = "HTTP $code for $currentUrl"
                    )
                }

                return response
            } catch (e: SocketTimeoutException) {
                throw SocketTimeoutException("Timeout while downloading $currentUrl: ${e.message}")
            } finally {
                // Ensure connection is released
                runCatching { conn.inputStream?.close() }
                runCatching { conn.errorStream?.close() }
                conn.disconnect()
            }
        }
    }

    private fun openConnection(urlStr: String, options: Options): HttpURLConnection {
        val url = URL(urlStr)
        val conn = (if (options.proxy != null) url.openConnection(options.proxy) else url.openConnection()) as HttpURLConnection

        // We'll handle redirects ourselves so we can cap and resolve relative URLs.
        conn.instanceFollowRedirects = false

        conn.connectTimeout = options.connectTimeoutMs
        conn.readTimeout = options.readTimeoutMs

        conn.requestMethod = "GET"
        conn.useCaches = false

        conn.setRequestProperty("User-Agent", options.userAgent)
        conn.setRequestProperty("Accept-Encoding", options.acceptEncoding)
        conn.setRequestProperty("Accept", "*/*")

        return conn
    }

    private fun isRedirect(code: Int): Boolean =
        code == HttpURLConnection.HTTP_MOVED_PERM || // 301
            code == HttpURLConnection.HTTP_MOVED_TEMP || // 302
            code == HttpURLConnection.HTTP_SEE_OTHER || // 303
            code == 307 || // Temporary Redirect
            code == 308    // Permanent Redirect

    private fun resolveRedirect(baseUrl: String, location: String): String {
        // Supports relative redirects
        val base = URL(baseUrl)
        val resolved = URL(base, location)
        return normalizeUrl(resolved.toString())
    }

    private fun decodeStream(input: InputStream, contentEncoding: String): InputStream {
        return when (contentEncoding) {
            "gzip" -> GZIPInputStream(input)
            "deflate" -> InflaterInputStream(input)
            // Sometimes servers send "br" (Brotli) — not supported in pure JDK8.
            // We'll just return raw stream; caller may get unreadable bytes.
            else -> input
        }
    }

    private fun readAllBytesLimited(input: InputStream, maxBytes: Long): ByteArray {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var total: Long = 0

            while (true) {
                val read = stream.read(buf)
                if (read < 0) break
                total += read.toLong()
                if (total > maxBytes) {
                    throw IOException("Response too large: exceeded maxBytes=$maxBytes")
                }
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun parseCharsetFromContentType(contentType: String): String? {
        // Example: "text/html; charset=UTF-8"
        val parts = contentType.split(";")
        for (p in parts) {
            val s = p.trim()
            if (s.startsWith("charset=", ignoreCase = true)) {
                return s.substringAfter("=", "").trim().trim('"', '\'')
            }
        }
        return null
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "URL is empty" }

        val url = URL(trimmed)
        val host = url.host
        if (host.isNullOrBlank()) return trimmed

        // IDN-safe host normalization (handles international domains).
        val asciiHost = try { IDN.toASCII(host) } catch (_: Exception) { host }

        // Rebuild if host changed
        return if (asciiHost == host) {
            trimmed
        } else {
            URL(url.protocol, asciiHost, url.port, url.file).toString()
        }
    }
}
