package ru.lionzxy.tplauncher.minecraft.connectivity

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.log.Logger
import java.io.File

/**
 * Windows-only JNA implementations for the connectivity-repair assistant. Bound via custom
 * [StdCallLibrary] interfaces + [Native.load] exactly like [ru.lionzxy.tplauncher.utils.WindowsPathHelper]
 * (base JNA, no jna-platform). Every entry point guards on the OS and degrades on any JNA failure —
 * these never crash the launcher. Runtime behaviour is verified on Windows; unit tests cover only the
 * pure helpers ([assembleBinaryCandidates], [SecurityProductClassifier]).
 */
private val isWindows: Boolean
    get() = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS

private interface Kernel32Ext : StdCallLibrary {
    fun GetModuleFileNameW(hModule: Pointer?, lpFilename: CharArray, nSize: Int): Int
    fun WaitForSingleObject(hHandle: Pointer, dwMilliseconds: Int): Int
    fun CloseHandle(hObject: Pointer): Boolean
    fun GetLastError(): Int
}

private val kernel32: Kernel32Ext? by lazy {
    if (!isWindows) return@lazy null
    try {
        Native.load("kernel32", Kernel32Ext::class.java)
    } catch (t: Throwable) {
        Logger.w("Connectivity", "Failed to load kernel32", t)
        null
    }
}

/** Full path of the current process's executable image (what Windows Firewall keys rules on). */
internal fun currentProcessImage(): File? {
    val k = kernel32 ?: return null
    return try {
        val buf = CharArray(1024)
        val len = k.GetModuleFileNameW(null, buf, buf.size)
        if (len in 1 until buf.size) File(String(buf, 0, len)) else null
    } catch (t: Throwable) {
        Logger.w("Connectivity", "GetModuleFileNameW failed", t)
        null
    }
}

/** Resolves the launcher's own runtime binaries plus the modpack's bundled JRE binaries. */
class WindowsBinaryResolver(private val bundledJreBinDir: File?) : BinaryResolver {
    override fun resolve(): List<File> {
        if (!isWindows) return emptyList()
        val javaHome = System.getProperty("java.home")?.let(::File)
        return assembleBinaryCandidates(
            processImage = currentProcessImage(),
            javaHome = javaHome,
            bundledJreBinDir = bundledJreBinDir,
            exists = File::exists,
        )
    }
}

@Structure.FieldOrder(
    "cbSize", "fMask", "hwnd", "lpVerb", "lpFile", "lpParameters", "lpDirectory", "nShow",
    "hInstApp", "lpIDList", "lpClass", "hkeyClass", "dwHotKey", "hIconOrMonitor", "hProcess",
)
internal class ShellExecuteInfo : Structure() {
    @JvmField var cbSize: Int = 0
    @JvmField var fMask: Int = 0
    @JvmField var hwnd: Pointer? = null
    @JvmField var lpVerb: WString? = null
    @JvmField var lpFile: WString? = null
    @JvmField var lpParameters: WString? = null
    @JvmField var lpDirectory: WString? = null
    @JvmField var nShow: Int = 0
    @JvmField var hInstApp: Pointer? = null
    @JvmField var lpIDList: Pointer? = null
    @JvmField var lpClass: WString? = null
    @JvmField var hkeyClass: Pointer? = null
    @JvmField var dwHotKey: Int = 0
    @JvmField var hIconOrMonitor: Pointer? = null
    @JvmField var hProcess: Pointer? = null
}

private interface Shell32 : StdCallLibrary {
    fun ShellExecuteExW(info: ShellExecuteInfo): Boolean
}

private val shell32: Shell32? by lazy {
    if (!isWindows) return@lazy null
    try {
        Native.load("shell32", Shell32::class.java)
    } catch (t: Throwable) {
        Logger.w("Connectivity", "Failed to load shell32", t)
        null
    }
}

/**
 * Elevates a script via `ShellExecuteExW(verb="runas")` (a single UAC prompt) and waits for it to
 * finish. Returns [ElevationResult.Success] once the elevated process exits (success meaning "it ran",
 * not "connectivity fixed" — the probe decides that). A UAC decline is best-effort detected via
 * `GetLastError()==ERROR_CANCELLED`; if that read is unreliable it safely degrades to [ElevationResult.Failed].
 */
class WindowsShellElevatedRunner : ElevatedRunner {
    private companion object {
        const val SEE_MASK_NOCLOSEPROCESS = 0x00000040
        const val SW_HIDE = 0
        const val ERROR_CANCELLED = 1223
        const val WAIT_TIMEOUT_MS = 120_000
    }

    override fun runElevated(scriptFile: File): ElevationResult {
        if (!isWindows) return ElevationResult.Failed("not Windows")
        val shell = shell32 ?: return ElevationResult.Failed("shell32 unavailable")
        val k = kernel32 ?: return ElevationResult.Failed("kernel32 unavailable")
        val info = ShellExecuteInfo().apply {
            cbSize = size()
            fMask = SEE_MASK_NOCLOSEPROCESS
            lpVerb = WString("runas")
            lpFile = WString("cmd.exe")
            lpParameters = WString("/c \"${scriptFile.absolutePath}\"")
            nShow = SW_HIDE
        }
        val ok = try {
            shell.ShellExecuteExW(info)
        } catch (t: Throwable) {
            Logger.w("Connectivity", "ShellExecuteExW threw", t)
            return ElevationResult.Failed(t.message ?: "ShellExecuteExW error")
        }
        if (!ok) {
            val err = try {
                k.GetLastError()
            } catch (t: Throwable) {
                0
            }
            return if (err == ERROR_CANCELLED) ElevationResult.UserCancelled
            else ElevationResult.Failed("ShellExecuteExW failed (code $err)")
        }
        val handle = info.hProcess ?: return ElevationResult.Success
        return try {
            k.WaitForSingleObject(handle, WAIT_TIMEOUT_MS)
            ElevationResult.Success
        } catch (t: Throwable) {
            Logger.w("Connectivity", "WaitForSingleObject failed", t)
            ElevationResult.Success
        } finally {
            try {
                k.CloseHandle(handle)
            } catch (t: Throwable) {
                Logger.w("Connectivity", "CloseHandle failed", t)
            }
        }
    }
}

/** Reads installed AV product display names from WMI (`root/SecurityCenter2`) via PowerShell. Unprivileged. */
class WindowsWmiSecurityProductDetector : SecurityProductDetector {
    override fun detect(): DetectedSecurity {
        if (!isWindows) return DetectedSecurity(emptyList(), AvClass.NONE_DETECTED)
        val names = try {
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | " +
                    "Select-Object -ExpandProperty displayName",
            ).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (t: Throwable) {
            Logger.w("Connectivity", "AV detection failed", t)
            emptyList()
        }
        return SecurityProductClassifier.classify(names)
    }
}
