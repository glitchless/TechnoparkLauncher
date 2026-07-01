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
    // NOTE: GetLastError must NOT be mapped as a JNA function — by the time a second JNA dispatch
    // runs, the thread's last-error may already be clobbered. Use Native.getLastError(), which JNA
    // captures immediately after each native call.
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

/**
 * Resolves the launcher's own runtime binaries plus the modpack's bundled JRE binaries.
 * [bundledJreBinDir] is a provider, resolved fresh on every [resolve]: the bundled JRE may not
 * exist yet when the orchestrator is constructed (first launch blocked before the JRE download)
 * but be installed by the time the user actually runs the firewall fix.
 */
class WindowsBinaryResolver(private val bundledJreBinDir: () -> File?) : BinaryResolver {
    override fun resolve(): List<File> {
        if (!isWindows) return emptyList()
        val javaHome = System.getProperty("java.home")?.let(::File)
        return assembleBinaryCandidates(
            processImage = currentProcessImage(),
            javaHome = javaHome,
            bundledJreBinDir = bundledJreBinDir(),
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
 * finish. Returns [ElevationResult.Success] only when the elevated process observably exited
 * (success meaning "it ran", not "connectivity fixed" — the probe decides that); a wait timeout or
 * wait failure degrades to [ElevationResult.Failed] so the caller doesn't probe a half-applied fix
 * as if it were complete. A UAC decline is detected via [Native.getLastError] == `ERROR_CANCELLED`
 * (captured by JNA right after the call) with the struct's `hInstApp == SE_ERR_ACCESSDENIED` as a
 * fallback; if both are unreliable it safely degrades to [ElevationResult.Failed].
 */
class WindowsShellElevatedRunner : ElevatedRunner {
    private companion object {
        const val SEE_MASK_NOCLOSEPROCESS = 0x00000040
        const val SW_HIDE = 0
        const val ERROR_CANCELLED = 1223
        const val SE_ERR_ACCESSDENIED = 5L
        const val WAIT_TIMEOUT_MS = 120_000
        const val WAIT_OBJECT_0 = 0
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
            // Native.getLastError() is captured by JNA immediately after the ShellExecuteExW
            // dispatch, before anything can clobber the thread's last-error.
            val err = Native.getLastError()
            val hInst = Pointer.nativeValue(info.hInstApp)
            return if (err == ERROR_CANCELLED || hInst == SE_ERR_ACCESSDENIED) {
                ElevationResult.UserCancelled
            } else {
                ElevationResult.Failed("ShellExecuteExW failed (code $err, hInstApp $hInst)")
            }
        }
        val handle = info.hProcess ?: return ElevationResult.Success
        return try {
            when (val wait = k.WaitForSingleObject(handle, WAIT_TIMEOUT_MS)) {
                WAIT_OBJECT_0 -> ElevationResult.Success
                else -> ElevationResult.Failed("elevated script did not finish (wait result $wait)")
            }
        } catch (t: Throwable) {
            Logger.w("Connectivity", "WaitForSingleObject failed", t)
            ElevationResult.Failed("could not wait for the elevated script")
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
    private companion object {
        const val DETECT_TIMEOUT_SECONDS = 10L
    }

    override fun detect(): DetectedSecurity {
        if (!isWindows) return DetectedSecurity(emptyList(), AvClass.NONE_DETECTED)
        val names = try {
            val proc = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | " +
                    "Select-Object -ExpandProperty displayName",
            ).redirectErrorStream(true).start()
            // Bounded wait: a security product may suspend the spawned powershell.exe — exactly on
            // the machines this detector targets — and an unbounded readText() would hang the
            // caller forever. The query's output is far below the pipe buffer, so waiting before
            // reading cannot deadlock.
            if (!proc.waitFor(DETECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                Logger.w("Connectivity", "AV detection timed out after ${DETECT_TIMEOUT_SECONDS}s")
                return SecurityProductClassifier.classify(emptyList())
            }
            proc.inputStream.bufferedReader().readText()
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (t: Throwable) {
            Logger.w("Connectivity", "AV detection failed", t)
            emptyList()
        }
        return SecurityProductClassifier.classify(names)
    }
}
