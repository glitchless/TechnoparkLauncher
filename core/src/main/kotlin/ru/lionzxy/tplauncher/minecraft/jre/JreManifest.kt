package ru.lionzxy.tplauncher.minecraft.jre

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import ru.lionzxy.tplauncher.config.generateSHA256
import java.io.File

/** One JRE variant (a `code` such as "jre8") and its per-platform downloadable files. */
data class JreManifestEntry(
    val code: String,
    val files: List<JreFile>,
)

/** A single platform's JRE archive within a [JreManifestEntry]. */
data class JreFile(
    val type: String,             // "Linux" | "Windows" | "macOS"
    val arch: String,             // "x86_64" | "arm64"
    val extension: String,        // "tar.gz" | "zip"
    val downloadUrl: String,
    val javaRelativePath: String, // path to the java binary inside the extracted archive
    @SerializedName("SHA-256") val sha256: String,         // base64 SHA-256 of the archive
    @SerializedName("javaSHA-256") val javaSha256: String, // base64 SHA-256 of the java binary
)

private val gson = Gson()

/** Parses the top-level JSON array of [JreManifestEntry]. */
fun parseJreManifest(json: String): List<JreManifestEntry> =
    gson.fromJson(json, Array<JreManifestEntry>::class.java).toList()

/** The entry whose [JreManifestEntry.code] equals [code], or null. */
fun List<JreManifestEntry>.findByCode(code: String): JreManifestEntry? =
    firstOrNull { it.code == code }

/**
 * The [JreFile] matching this machine. [osName] is the jres2.json `type`
 * ("Windows"/"macOS"/"Linux"); [archAliases] are accepted arch names from
 * oslib `Arch.getSearch()` (e.g. ["x86_64","amd64","k8"] or ["ARM","arm64"]).
 */
fun JreManifestEntry.selectFile(osName: String, archAliases: List<String>): JreFile? =
    files.firstOrNull { f ->
        f.type.equals(osName, ignoreCase = true) &&
            archAliases.any { it.equals(f.arch, ignoreCase = true) }
    }

/** True if [binary] exists and its base64 SHA-256 equals [expectedBase64Sha]. */
fun isJavaBinaryValid(binary: File, expectedBase64Sha: String): Boolean =
    binary.exists() && runCatching { binary.generateSHA256() }.getOrNull() == expectedBase64Sha
