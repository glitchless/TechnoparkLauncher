package ru.lionzxy.tplauncher.minecraft.jre

import io.sentry.Sentry
import kotlinx.coroutines.runBlocking
import nu.redpois0n.oslib.OperatingSystem
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import ru.lionzxy.tplauncher.config.generateSHA256
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.minecraft.JRES_JSON_LINK
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.TextProgressMonitor
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal const val JRE_LOG_TAG = "JreManager"

/**
 * Fetches and parses the JRE manifest. On success the raw JSON is written to [cacheFile] so a later
 * offline run can still resolve an already-installed JRE. On network failure the cached JSON is used.
 * Throws if neither the network nor the cache yields a manifest.
 */
internal suspend fun fetchJreManifest(
    http: HttpDownloader,
    url: String,
    cacheFile: File,
): List<JreManifestEntry> {
    val networkJson = runCatching { http.getString(url) }
        .onFailure { e ->
            Logger.w(JRE_LOG_TAG, "Failed to fetch JRE manifest from $url; falling back to cache", e)
            Sentry.captureException(e)
        }
        .getOrNull()

    if (networkJson != null) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(networkJson)
        }.onFailure { Logger.w(JRE_LOG_TAG, "Failed to write JRE manifest cache", it) }
        return parseJreManifest(networkJson)
    }

    if (cacheFile.exists()) {
        Logger.i(JRE_LOG_TAG, "Using cached JRE manifest at ${cacheFile.absolutePath}")
        return parseJreManifest(cacheFile.readText())
    }

    throw IOException("JRE manifest unavailable: network failed and no cache at ${cacheFile.absolutePath}")
}

/** This machine's JRE selection criteria. [osName] is null on an unsupported OS. */
data class JrePlatform(
    val osName: String?,
    val archAliases: List<String>,
    val isWindows: Boolean,
) {
    companion object {
        fun current(): JrePlatform {
            val os = OperatingSystem.getOperatingSystem()
            val osName = when (os.type) {
                OperatingSystem.WINDOWS -> "Windows"
                OperatingSystem.MACOS -> "macOS"
                OperatingSystem.LINUX -> "Linux"
                else -> null
            }
            return JrePlatform(osName, os.arch.search.toList(), os.type == OperatingSystem.WINDOWS)
        }
    }
}

/** Extracts [archive] ([extension] = "tar.gz" or "zip") into [dest], preserving entry file modes. */
internal fun extractArchive(archive: File, extension: String, dest: File) {
    dest.mkdirs()
    val archiver = when {
        extension.equals("tar.gz", ignoreCase = true) || extension.equals("tgz", ignoreCase = true) ->
            ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
        extension.equals("zip", ignoreCase = true) ->
            ArchiverFactory.createArchiver(ArchiveFormat.ZIP)
        else -> throw IllegalArgumentException("Unsupported JRE archive extension: $extension")
    }
    archiver.extract(archive, dest)
}

/**
 * Installs and resolves per-code managed JREs. All collaborators are injected so the orchestration
 * is testable without real disk layout, network, or archives; [instance] wires the production seams.
 */
class JreManager(
    private val http: HttpDownloader,
    private val manifestUrl: String,
    private val manifestCacheFile: File,
    private val installDirFor: (code: String) -> File,
    private val platform: JrePlatform,
    private val extract: (archive: File, extension: String, dest: File) -> Unit = ::extractArchive,
) {
    private val resolved = ConcurrentHashMap<String, File>()

    /**
     * Ensures the JRE for [code] is installed for this platform and returns its java binary.
     * Reuses an already-installed, hash-valid JRE (no download). Hard-fails (throws) when the JRE
     * cannot be provisioned and is not already installed.
     */
    fun ensureInstalled(code: String, monitor: IProgressMonitor): File {
        val osName = platform.osName
            ?: throw IOException("Unsupported operating system for managed JRE '$code'")
        val manifest = runBlocking { fetchJreManifest(http, manifestUrl, manifestCacheFile) }
        val entry = manifest.findByCode(code)
            ?: throw IOException("JRE manifest has no entry for code '$code'")
        val file = entry.selectFile(osName, platform.archAliases)
            ?: throw IOException("No JRE '$code' build for $osName/${platform.archAliases.firstOrNull()}")

        val installDir = installDirFor(code)
        val binary = binaryPath(installDir, file)

        if (isJavaBinaryValid(binary, file.javaSha256)) {
            Logger.i(JRE_LOG_TAG, "JRE '$code' already installed at ${binary.absolutePath}")
            resolved[code] = binary
            return binary
        }

        Logger.i(JRE_LOG_TAG, "Installing JRE '$code' for $osName")
        val tmp = File.createTempFile("jre-$code-", ".${file.extension}")
        try {
            monitor.setStatus("Загрузка Java...")
            val progress = TextProgressMonitor("Загрузка Java... %s", monitor)
            runBlocking {
                http.downloadToFile(file.downloadUrl, tmp) { read, total ->
                    if (total != null) progress.setMax(total.toInt())
                    progress.setProgress(read.toInt())
                }
            }
            val archiveSha = tmp.generateSHA256()
            if (archiveSha != file.sha256) {
                throw IOException("JRE '$code' archive checksum mismatch (expected ${file.sha256}, got $archiveSha)")
            }

            monitor.setStatus("Распаковка Java...")
            monitor.setProgress(-1) // -1 = indeterminate progress (no byte total during extraction)
            installDir.deleteDirectoryRecursionJava6()
            extract(tmp, file.extension, installDir)
        } finally {
            tmp.delete()
        }

        if (!isJavaBinaryValid(binary, file.javaSha256)) {
            throw IOException("JRE '$code' java binary missing or corrupt after extraction: ${binary.absolutePath}")
        }
        if (!platform.isWindows) {
            binary.setExecutable(true)
        }
        resolved[code] = binary
        return binary
    }

    /**
     * Resolves the installed java binary for [code] without touching the network — from the in-memory
     * cache, then the on-disk manifest cache. Null when nothing is installed.
     */
    fun resolveJavaBinary(code: String): File? {
        resolved[code]?.let { return it }
        val osName = platform.osName ?: return null
        val manifest = runCatching {
            if (manifestCacheFile.exists()) parseJreManifest(manifestCacheFile.readText()) else null
        }.getOrNull() ?: return null
        val file = manifest.findByCode(code)?.selectFile(osName, platform.archAliases) ?: return null
        val binary = binaryPath(installDirFor(code), file)
        return if (isJavaBinaryValid(binary, file.javaSha256)) binary.also { resolved[code] = it } else null
    }

    /** The java binary inside [installDir]; on Windows the GUI `javaw.exe` sibling of `java.exe`. */
    private fun binaryPath(installDir: File, file: JreFile): File {
        val raw = File(installDir, file.javaRelativePath)
        return if (platform.isWindows) File(raw.parentFile, "javaw.exe") else raw
    }

    companion object {
        val instance: JreManager by lazy {
            JreManager(
                http = HttpDownloader.instance,
                manifestUrl = JRES_JSON_LINK,
                manifestCacheFile = ConfigHelper.getJreManifestCacheFile(),
                installDirFor = ConfigHelper::getJreInstallDirectory,
                platform = JrePlatform.current(),
            )
        }
    }
}
