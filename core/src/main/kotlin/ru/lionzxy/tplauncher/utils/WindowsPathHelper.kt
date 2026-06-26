package ru.lionzxy.tplauncher.utils

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.log.Logger
import java.io.File
import java.nio.charset.Charset

/**
 * Helpers for surviving non-ASCII (e.g. Cyrillic) install paths on Windows.
 *
 * JDK 8 encodes file/library/argument paths through `sun.jnu.encoding` (the system ANSI codepage)
 * and loads native libraries via the ANSI `LoadLibraryA` (JDK-8195129, unfixed in 8). If the active
 * codepage can't represent the characters in the path — a Cyrillic username on a non-Russian Windows,
 * for example — the path is corrupted to `?` and LWJGL/JRE natives fail to load. Resolving the path to
 * its 8.3 short name (pure ASCII) before handing it to the child JVM sidesteps the whole conversion.
 */
object WindowsPathHelper {
    private val isWindows = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS

    private interface Kernel32 : StdCallLibrary {
        // DWORD GetShortPathNameW(LPCWSTR lpszLongPath, LPWSTR lpszShortPath, DWORD cchBuffer)
        fun GetShortPathNameW(lpszLongPath: WString, lpszShortPath: CharArray?, cchBuffer: Int): Int
    }

    private val kernel32: Kernel32? by lazy {
        if (!isWindows) return@lazy null
        try {
            Native.load("kernel32", Kernel32::class.java)
        } catch (t: Throwable) {
            Logger.w("WindowsPath", "Failed to load kernel32; short-path support unavailable", t)
            null
        }
    }

    /**
     * Returns the 8.3 short-name (ASCII) form of [file] on Windows. Returns [file] unchanged when:
     * not on Windows, the path doesn't exist, 8.3 generation is disabled for the volume (the short
     * name equals the long name), or any native call fails. Callers should treat a returned path that
     * still contains non-ASCII characters as "no ASCII alias available".
     */
    fun toShortPath(file: File): File {
        val k = kernel32 ?: return file
        if (!file.exists()) return file
        return try {
            val longPath = WString(file.absolutePath)
            val needed = k.GetShortPathNameW(longPath, null, 0)
            if (needed <= 0) return file
            val buffer = CharArray(needed)
            val len = k.GetShortPathNameW(longPath, buffer, needed)
            // On success len excludes the terminating NUL, so len < needed.
            if (len <= 0 || len >= needed) return file
            val short = String(buffer, 0, len)
            if (short.isEmpty()) file else File(short)
        } catch (t: Throwable) {
            Logger.w("WindowsPath", "Failed to resolve 8.3 short path for ${file.absolutePath}", t)
            file
        }
    }

    /** True if every character of [text] is plain ASCII. */
    fun isAscii(text: String): Boolean = text.all { it.toInt() < 128 }

    /**
     * True if [text] can be encoded in the JVM's `sun.jnu.encoding` (the encoding JDK 8 uses for
     * file/argument paths on Windows). When false, a path with these characters cannot survive being
     * handed to the OS and the only reliable fix is an ASCII path.
     */
    fun isRepresentableInSystemEncoding(text: String): Boolean {
        return try {
            val encoding = System.getProperty("sun.jnu.encoding") ?: return true
            Charset.forName(encoding).newEncoder().canEncode(text)
        } catch (t: Throwable) {
            true
        }
    }
}
