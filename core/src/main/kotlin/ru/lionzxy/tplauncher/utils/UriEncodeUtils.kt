package ru.lionzxy.tplauncher.utils

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Code from Spring Framework.
 */
object UriEncodeUtils {

    fun encodePath(source: String, charset: Charset): String {
        if (!hasLength(source)) {
            return source
        }

        val bytes = source.toByteArray(charset)
        var original = true
        for (b in bytes) {
            if (!isAllowed(b.toInt())) {
                original = false
                break
            }
        }
        if (original) {
            return source
        }

        val baos = ByteArrayOutputStream(bytes.size)
        for (b in bytes) {
            if (isAllowed(b.toInt())) {
                baos.write(b.toInt())
            } else {
                baos.write('%'.code)
                val hex1 = Character.toUpperCase(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                val hex2 = Character.toUpperCase(Character.forDigit(b.toInt() and 0xF, 16))
                baos.write(hex1.code)
                baos.write(hex2.code)
            }
        }
        return baos.toString(charset)
    }

    private fun isAllowed(c: Int): Boolean = isPchar(c) || '/'.code == c

    /** Whether [c] is in the `ALPHA` set (RFC 3986, appendix A). */
    private fun isAlpha(c: Int): Boolean =
        (c >= 'a'.code && c <= 'z'.code) || (c >= 'A'.code && c <= 'Z'.code)

    /** Whether [c] is in the `DIGIT` set (RFC 3986, appendix A). */
    private fun isDigit(c: Int): Boolean = c >= '0'.code && c <= '9'.code

    /** Whether [c] is in the `sub-delims` set (RFC 3986, appendix A). */
    private fun isSubDelimiter(c: Int): Boolean =
        '!'.code == c || '$'.code == c || '&'.code == c || '\''.code == c || '('.code == c ||
            ')'.code == c || '*'.code == c || '+'.code == c || ','.code == c || ';'.code == c ||
            '='.code == c

    /** Whether [c] is in the `unreserved` set (RFC 3986, appendix A). */
    private fun isUnreserved(c: Int): Boolean =
        isAlpha(c) || isDigit(c) || '-'.code == c || '.'.code == c || '_'.code == c || '~'.code == c

    /** Whether [c] is in the `pchar` set (RFC 3986, appendix A). */
    private fun isPchar(c: Int): Boolean = isUnreserved(c) || isSubDelimiter(c) || ':'.code == c || '@'.code == c

    /** True when [str] is neither null nor empty (whitespace counts as non-empty). */
    private fun hasLength(str: String?): Boolean = str != null && str.isNotEmpty()
}
