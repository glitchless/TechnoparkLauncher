package ru.lionzxy.tplauncher.utils;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Code from Spring Framework
 */
public class UriEncodeUtils {
    public static String encodePath(String source, Charset charset) {
        if (!hasLength(source)) {
            return source;
        }

        byte[] bytes = source.getBytes(charset);
        boolean original = true;
        for (byte b : bytes) {
            if (!isAllowed(b)) {
                original = false;
                break;
            }
        }
        if (original) {
            return source;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
        for (byte b : bytes) {
            if (isAllowed(b)) {
                baos.write(b);
            } else {
                baos.write('%');
                char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, 16));
                char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                baos.write(hex1);
                baos.write(hex2);
            }
        }
        return copyToString(baos, charset);
    }

    private static boolean isAllowed(int c) {
        return isPchar(c) || '/' == c;
    }

    /**
     * Indicates whether the given character is in the {@code ALPHA} set.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    private static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z');
    }

    /**
     * Indicates whether the given character is in the {@code DIGIT} set.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    private static boolean isDigit(int c) {
        return (c >= '0' && c <= '9');
    }

    /**
     * Indicates whether the given character is in the {@code sub-delims} set.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    private static boolean isSubDelimiter(int c) {
        return ('!' == c || '$' == c || '&' == c || '\'' == c || '(' == c || ')' == c || '*' == c || '+' == c ||
                ',' == c || ';' == c || '=' == c);
    }

    /**
     * Indicates whether the given character is in the {@code unreserved} set.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    private static boolean isUnreserved(int c) {
        return (isAlpha(c) || isDigit(c) || '-' == c || '.' == c || '_' == c || '~' == c);
    }

    /**
     * Indicates whether the given character is in the {@code pchar} set.
     *
     * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
     */
    private static boolean isPchar(int c) {
        return (isUnreserved(c) || isSubDelimiter(c) || ':' == c || '@' == c);
    }

    /**
     * Check that the given {@code String} is neither {@code null} nor of length 0.
     * <p>Note: this method returns {@code true} for a {@code String} that
     * purely consists of whitespace.
     *
     * @param str the {@code String} to check (may be {@code null})
     * @return {@code true} if the {@code String} is not {@code null} and has length
     */
    private static boolean hasLength(String str) {
        return (str != null && !str.isEmpty());
    }


    /**
     * Copy the contents of the given {@link ByteArrayOutputStream} into a {@link String}.
     * <p>This is a more effective equivalent of {@code new String(baos.toByteArray(), charset)}.
     *
     * @param baos    the {@code ByteArrayOutputStream} to be copied into a String
     * @param charset the {@link Charset} to use to decode the bytes
     * @return the String that has been copied to (possibly empty)
     * @since 5.2.6
     */
    private static String copyToString(ByteArrayOutputStream baos, Charset charset) {
        try {
            // Can be replaced with toString(Charset) call in Java 10+
            return baos.toString(charset.name());
        } catch (UnsupportedEncodingException ex) {
            // Should never happen
            throw new IllegalArgumentException("Invalid charset name: " + charset, ex);
        }
    }
}
