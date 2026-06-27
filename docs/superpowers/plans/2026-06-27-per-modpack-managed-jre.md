# Per-modpack Managed JRE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Each modpack declares a JRE code (`jre8` by default); the launcher fetches `jres2.json`, downloads/verifies/extracts the matching JRE for the current OS/arch, and launches with it — replacing the single hardcoded `jrepath.txt`.

**Architecture:** A pure manifest model + platform-selection layer (`JreManifest.kt`), an offline-tolerant manifest fetcher (`fetchJreManifest`), and an I/O orchestrator (`JreManager`) that installs and resolves the JRE binary. A new `JreDownloader` provisions the JRE first in the prepare pipeline; `MinecraftLauncher` resolves the installed binary and injects it into `LauncherSettings`. The legacy `jrepath.txt` mechanism and the manual Java-path setting are deleted.

**Tech Stack:** Kotlin/JVM (toolchain 21), Gson, Ktor (`HttpDownloader`), `jarchivelib` (tar.gz/zip extraction), commons-codec / `MessageDigest` (base64 SHA-256 via existing `File.generateSHA256()`), `oslib` (OS/arch detection), JUnit4 + Ktor `MockEngine` + coroutines-test.

## Global Constraints

- Package root: `ru.lionzxy.tplauncher`. New JRE code lives in `core/.../minecraft/jre/`.
- Manifest URL: `https://minecraft.glitchless.ru/jres2.json` (= `"$BASE_URL/jres2.json"`).
- Default JRE code per modpack: `"jre8"` (Kotlin enum-constructor default).
- jres2.json `type` values are `Linux` / `Windows` / `macOS`; `arch` values are `x86_64` / `arm64`; `extension` is `tar.gz` (Linux/macOS) or `zip` (Windows).
- Hash format is **base64 SHA-256**, produced by the existing `ru.lionzxy.tplauncher.config.generateSHA256` (`File.generateSHA256(): String?`). `SHA-256` = archive hash; `javaSHA-256` = extracted java-binary hash.
- OS match: jres2 `type` compared (ignore-case) to a mapped OS name. Arch match: jres2 `arch` compared (ignore-case) against `oslib Arch.getSearch()` aliases (`x86_64`→`["x86_64","amd64","k8"]`, `arm64`→`["ARM","arm64"]`).
- On Windows the launch binary is `javaw.exe` (sibling of `java.exe`); the existing Windows non-ASCII 8.3 short-path logic in `LauncherSettings` is preserved.
- Hard-fail (throw) when the required JRE is not installed and cannot be provisioned. An already-installed, hash-valid JRE is always reused (offline-friendly).
- Cache the latest `jres2.json` to disk (`technomine/jre/jres2.json`) for offline resolution.
- Status strings shown to the user are Russian (core layer uses literal strings, matching `InitialDownloader`/`IncrementalDownloader`).
- Commit after every task. Keep the build green at each commit.

---

### Task 1: Manifest model + platform selection (pure)

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifest.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestTest.kt`

**Interfaces:**
- Consumes: `ru.lionzxy.tplauncher.config.generateSHA256` (existing `File.generateSHA256(): String?`).
- Produces:
  - `data class JreManifestEntry(val code: String, val files: List<JreFile>)`
  - `data class JreFile(type, arch, extension, downloadUrl, javaRelativePath, sha256, javaSha256)` (all `String`)
  - `fun parseJreManifest(json: String): List<JreManifestEntry>`
  - `fun List<JreManifestEntry>.findByCode(code: String): JreManifestEntry?`
  - `fun JreManifestEntry.selectFile(osName: String, archAliases: List<String>): JreFile?`
  - `fun isJavaBinaryValid(binary: File, expectedBase64Sha: String): Boolean`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestTest.kt`:

```kotlin
package ru.lionzxy.tplauncher.minecraft.jre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.config.generateSHA256
import java.io.File
import java.nio.file.Files

class JreManifestTest {

    private val sampleJson = """
        [
          {"code":"jre21","files":[
            {"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u1","javaRelativePath":"jre-21/bin/java","SHA-256":"a","javaSHA-256":"ja"},
            {"type":"macOS","arch":"arm64","extension":"tar.gz","downloadUrl":"u2","javaRelativePath":"jre-21.jre/bin/java","SHA-256":"b","javaSHA-256":"jb"},
            {"type":"Windows","arch":"x86_64","extension":"zip","downloadUrl":"u3","javaRelativePath":"jre-21/bin/java.exe","SHA-256":"c","javaSHA-256":"jc"}
          ]},
          {"code":"jre8","files":[
            {"type":"macOS","arch":"x86_64","extension":"tar.gz","downloadUrl":"u4","javaRelativePath":"jdk8.jdk/bin/java","SHA-256":"d","javaSHA-256":"jd"}
          ]}
        ]
    """.trimIndent()

    @Test
    fun parsesAllEntriesAndMapsHashKeys() {
        val m = parseJreManifest(sampleJson)
        assertEquals(2, m.size)
        val jre21 = m.findByCode("jre21")!!
        assertEquals(3, jre21.files.size)
        val linux = jre21.files.first { it.type == "Linux" }
        assertEquals("a", linux.sha256)
        assertEquals("ja", linux.javaSha256)
        assertEquals("jre-21/bin/java", linux.javaRelativePath)
    }

    @Test
    fun findByCodeReturnsNullForUnknown() {
        assertNull(parseJreManifest(sampleJson).findByCode("jre99"))
    }

    @Test
    fun selectsMacArm64ForArmAliases() {
        val jre21 = parseJreManifest(sampleJson).findByCode("jre21")!!
        val f = jre21.selectFile("macOS", listOf("ARM", "arm64"))
        assertNotNull(f)
        assertEquals("u2", f!!.downloadUrl)
    }

    @Test
    fun selectsX64ForAmd64Aliases() {
        val jre21 = parseJreManifest(sampleJson).findByCode("jre21")!!
        val f = jre21.selectFile("Linux", listOf("x86_64", "amd64", "k8"))
        assertEquals("u1", f!!.downloadUrl)
    }

    @Test
    fun selectReturnsNullWhenNoPlatformMatch() {
        val jre8 = parseJreManifest(sampleJson).findByCode("jre8")!!
        assertNull(jre8.selectFile("Windows", listOf("x86_64")))     // jre8 has no Windows build here
        assertNull(jre8.selectFile("macOS", listOf("ARM", "arm64"))) // jre8 macOS is x86_64 only
    }

    @Test
    fun isJavaBinaryValidMatchesOwnHashAndRejectsMismatchOrMissing() {
        val dir = Files.createTempDirectory("jrebin").toFile()
        val bin = File(dir, "java").apply { writeBytes("#!/bin/sh\necho 21".toByteArray()) }
        val good = bin.generateSHA256()!!
        assertTrue(isJavaBinaryValid(bin, good))
        assertFalse(isJavaBinaryValid(bin, "deadbeef="))
        assertFalse(isJavaBinaryValid(File(dir, "nope"), good))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManifestTest"`
Expected: FAIL — compilation error, `unresolved reference: parseJreManifest` (and the other new symbols).

- [ ] **Step 3: Write the implementation**

Create `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifest.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManifestTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifest.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestTest.kt
git commit -m "Add JRE manifest model and platform selection"
```

---

### Task 2: Manifest URL constant + per-modpack JRE code

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftContext.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftModpackJreTest.kt` (create)

**Interfaces:**
- Produces: `const val JRES_JSON_LINK` (in package `ru.lionzxy.tplauncher.minecraft`); `MinecraftModpack.javaCode: String` (default `"jre8"`).

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftModpackJreTest.kt`:

```kotlin
package ru.lionzxy.tplauncher.minecraft

import org.junit.Assert.assertEquals
import org.junit.Test

class MinecraftModpackJreTest {

    @Test
    fun allCurrentModpacksDefaultToJre8() {
        MinecraftModpack.values().forEach {
            assertEquals("modpack ${it.modpackName} must default to jre8", "jre8", it.javaCode)
        }
    }

    @Test
    fun jresJsonLinkIsUnderBaseUrl() {
        assertEquals("https://minecraft.glitchless.ru/jres2.json", JRES_JSON_LINK)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.MinecraftModpackJreTest"`
Expected: FAIL — `unresolved reference: JRES_JSON_LINK` and `javaCode`.

- [ ] **Step 3: Add the constant and enum field**

In `MinecraftContext.kt`, add the constant next to the existing links (after line 11):

```kotlin
const val APPLE_SILICON_UPDATE_JSON_LINK = "$BASE_URL/incremental/asworkaround_changelog.json"
const val JRES_JSON_LINK = "$BASE_URL/jres2.json"
```

Add the `javaCode` parameter (with default) to the enum constructor:

```kotlin
enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String?,
    val updateJsonLink: String?,
    val updateHostLink: String?,
    val defaultServer: ServerInfo?,
    val version: String,
    val javaCode: String = "jre8",
) {
```

(The three existing entries — VANILLA / GTNH / NOMI — are left unchanged; they inherit the `jre8` default.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.MinecraftModpackJreTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftContext.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftModpackJreTest.kt
git commit -m "Add JRES_JSON_LINK and per-modpack javaCode (default jre8)"
```

---

### Task 3: Offline-tolerant manifest fetch + disk cache

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt` (fetch function only; the class is added in Task 4)
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestFetcherTest.kt`

**Interfaces:**
- Consumes: `parseJreManifest` (Task 1); `HttpDownloader` (`suspend fun getString(url): String`); `applyDefaults()` (internal Ktor config, `core` main); `Logger`.
- Produces: `internal suspend fun fetchJreManifest(http: HttpDownloader, url: String, cacheFile: File): List<JreManifestEntry>`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestFetcherTest.kt`:

```kotlin
package ru.lionzxy.tplauncher.minecraft.jre

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.applyDefaults
import java.io.File
import java.io.IOException
import java.nio.file.Files

class JreManifestFetcherTest {

    private val json =
        """[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u","javaRelativePath":"j/bin/java","SHA-256":"a","javaSHA-256":"ja"}]}]"""

    private fun okDownloader(body: String): HttpDownloader {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.length.toString()))
        }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    private fun offlineDownloader(): HttpDownloader {
        val engine = MockEngine { throw IOException("offline") }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    private fun tempFile(name: String) = File(Files.createTempDirectory("jrecache").toFile(), name)

    @Test
    fun onlineFetchParsesAndWritesCache() = runTest {
        val cache = tempFile("jres2.json")
        val m = fetchJreManifest(okDownloader(json), "https://h/jres2.json", cache)
        assertEquals("jre8", m.single().code)
        assertTrue("cache file must be written", cache.exists())
        assertEquals(json, cache.readText())
    }

    @Test
    fun offlineReadsFromCache() = runTest {
        val cache = tempFile("jres2.json").apply { writeText(json) }
        val m = fetchJreManifest(offlineDownloader(), "https://h/jres2.json", cache)
        assertEquals("jre8", m.single().code)
    }

    @Test
    fun offlineWithoutCacheThrows() = runTest {
        val cache = tempFile("missing.json") // does not exist
        try {
            fetchJreManifest(offlineDownloader(), "https://h/jres2.json", cache)
            fail("expected IOException when offline and no cache")
        } catch (e: IOException) {
            // expected
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManifestFetcherTest"`
Expected: FAIL — `unresolved reference: fetchJreManifest`.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt`:

```kotlin
package ru.lionzxy.tplauncher.minecraft.jre

import io.sentry.Sentry
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.utils.HttpDownloader
import java.io.File
import java.io.IOException

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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManifestFetcherTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManifestFetcherTest.kt
git commit -m "Add offline-tolerant JRE manifest fetch with disk cache"
```

---

### Task 4: JreManager orchestrator (install + resolve) + ConfigHelper dirs

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/ConfigHelper.kt` (add two dir helpers)
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt` (add `JrePlatform`, `extractArchive`, `JreManager`)
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManagerTest.kt`

**Interfaces:**
- Consumes: `fetchJreManifest` (Task 3); `JreManifestEntry.selectFile`, `findByCode`, `isJavaBinaryValid` (Task 1); `JRES_JSON_LINK` (Task 2); `HttpDownloader.instance`, `HttpDownloader.downloadToFile`; `TextProgressMonitor`; `generateSHA256`; `File.deleteDirectoryRecursionJava6`; `IProgressMonitor`; oslib `OperatingSystem`/`Arch`; jarchivelib `ArchiverFactory`/`ArchiveFormat`/`CompressionType`.
- Produces:
  - `ConfigHelper.getJreInstallDirectory(code: String): File` (= `technomine/jre/<code>`)
  - `ConfigHelper.getJreManifestCacheFile(): File` (= `technomine/jre/jres2.json`)
  - `data class JrePlatform(val osName: String?, val archAliases: List<String>, val isWindows: Boolean)` + `JrePlatform.current()`
  - `internal fun extractArchive(archive: File, extension: String, dest: File)`
  - `class JreManager(http, manifestUrl, manifestCacheFile, installDirFor, platform, extract)` with `fun ensureInstalled(code: String, monitor: IProgressMonitor): File`, `fun resolveJavaBinary(code: String): File?`, and `companion object { val instance: JreManager }`

- [ ] **Step 1: Add the ConfigHelper directory helpers**

In `ConfigHelper.kt`, immediately after the existing `getJavaDirectory()` (the `jre` dir helper), add:

```kotlin
    fun getJreInstallDirectory(code: String): File {
        val dir = File(getJavaDirectory(), code)
        dir.mkdirs()
        return dir
    }

    fun getJreManifestCacheFile(): File {
        return File(getJavaDirectory(), "jres2.json")
    }
```

(Leave `getJREPathFile()` / `writeJREConfig()` in place for now — they are removed in Task 7.)

- [ ] **Step 2: Write the failing test**

Create `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManagerTest.kt`:

```kotlin
package ru.lionzxy.tplauncher.minecraft.jre

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.lionzxy.tplauncher.config.generateSHA256
import ru.lionzxy.tplauncher.utils.HttpDownloader
import ru.lionzxy.tplauncher.utils.applyDefaults
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.io.File
import java.nio.file.Files

class JreManagerTest {

    private object NoopMonitor : IProgressMonitor {
        override fun setMax(len: Int) {}
        override fun setProgress(progress: Int) {}
        override fun setStatus(status: String?) {}
        override fun incrementProgress(amount: Int) {}
    }

    private fun okDownloader(body: String): HttpDownloader {
        val engine = MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, body.length.toString()))
        }
        return HttpDownloader(HttpClient(engine) { applyDefaults() })
    }

    @Test
    fun resolveJavaBinaryUsesCachedManifestWhenBinaryExists() {
        val tmp = Files.createTempDirectory("jremgr").toFile()
        val cache = File(tmp, "jres2.json").apply {
            writeText("""[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"u","javaRelativePath":"j/bin/java","SHA-256":"a","javaSHA-256":"ja"}]}]""")
        }
        val installDirFor = { code: String -> File(tmp, "jre/$code") }
        val bin = File(installDirFor("jre8"), "j/bin/java").apply { parentFile.mkdirs(); writeText("x") }

        val mgr = JreManager(
            http = okDownloader("[]"),
            manifestUrl = "https://unused",
            manifestCacheFile = cache,
            installDirFor = installDirFor,
            platform = JrePlatform("Linux", listOf("x86_64", "amd64"), isWindows = false),
            extract = { _, _, _ -> },
        )

        assertEquals(bin.absolutePath, mgr.resolveJavaBinary("jre8")!!.absolutePath)
        assertNull(mgr.resolveJavaBinary("jre99"))
    }

    @Test
    fun ensureInstalledSkipsDownloadWhenBinaryHashMatches() {
        val tmp = Files.createTempDirectory("jremgr2").toFile()
        val installDirFor = { code: String -> File(tmp, "jre/$code") }
        val bin = File(installDirFor("jre8"), "j/bin/java").apply {
            parentFile.mkdirs(); writeBytes("real-java-binary".toByteArray())
        }
        val sha = bin.generateSHA256()!!
        val manifest =
            """[{"code":"jre8","files":[{"type":"Linux","arch":"x86_64","extension":"tar.gz","downloadUrl":"http://no","javaRelativePath":"j/bin/java","SHA-256":"arch","javaSHA-256":"$sha"}]}]"""

        val mgr = JreManager(
            http = okDownloader(manifest),
            manifestUrl = "https://h/jres2.json",
            manifestCacheFile = File(tmp, "cache.json"),
            installDirFor = installDirFor,
            platform = JrePlatform("Linux", listOf("x86_64"), isWindows = false),
            extract = { _, _, _ -> throw IllegalStateException("must not extract when already installed") },
        )

        assertEquals(bin.absolutePath, mgr.ensureInstalled("jre8", NoopMonitor).absolutePath)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManagerTest"`
Expected: FAIL — `unresolved reference: JreManager` / `JrePlatform`.

- [ ] **Step 4: Write the implementation**

Append to `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt` (add the imports to the existing import block, then the new declarations):

```kotlin
import kotlinx.coroutines.runBlocking
import nu.redpois0n.oslib.OperatingSystem
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType
import ru.lionzxy.tplauncher.config.generateSHA256
import ru.lionzxy.tplauncher.minecraft.JRES_JSON_LINK
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.TextProgressMonitor
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import java.util.concurrent.ConcurrentHashMap
```

```kotlin
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
            monitor.setProgress(-1)
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
        return if (binary.exists()) binary.also { resolved[code] = it } else null
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.jre.JreManagerTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Run the whole core module to confirm no regressions**

Run: `./gradlew :core:test`
Expected: PASS (all core tests).

- [ ] **Step 7: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/utils/ConfigHelper.kt \
        core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManager.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/jre/JreManagerTest.kt
git commit -m "Add JreManager install/resolve orchestrator and JRE install dirs"
```

---

### Task 5: Provision the JRE in the prepare pipeline

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/JreDownloader.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/ComposerDownloader.kt`

**Interfaces:**
- Consumes: `IDownloader` (`init`/`download`/`shouldDownload`); `MinecraftContext.modpack.javaCode`, `MinecraftContext.progressMonitor`; `JreManager.instance.ensureInstalled`.
- Produces: `class JreDownloader : IDownloader`.

- [ ] **Step 1: Create the downloader**

Create `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/JreDownloader.kt`:

```kotlin
package ru.lionzxy.tplauncher.prepare.downloader

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.jre.JreManager

/**
 * Provisions the per-modpack managed JRE before any modpack content is downloaded. A failure here
 * aborts the launch (hard-fail) rather than falling back to a possibly-wrong system Java.
 */
class JreDownloader : IDownloader {
    override fun init(minecraft: MinecraftContext) {}

    override fun download(minecraft: MinecraftContext) {
        JreManager.instance.ensureInstalled(minecraft.modpack.javaCode, minecraft.progressMonitor)
    }

    override fun shouldDownload(minecraft: MinecraftContext) = true
}
```

- [ ] **Step 2: Register it first in ComposerDownloader**

In `ComposerDownloader.kt`, change the `downloaders` list so `JreDownloader()` runs first:

```kotlin
    val downloaders = listOf(
        JreDownloader(),
        InitialDownloader(),
        UpdateDownloader(),
        NativeAppleSiliconDownloader(),
        MinecraftDownloader()
    )
```

- [ ] **Step 3: Verify the module compiles and tests pass**

Run: `./gradlew :core:test`
Expected: PASS (no regressions; the new class is wiring only).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/JreDownloader.kt \
        core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/ComposerDownloader.kt
git commit -m "Provision managed JRE first in the prepare pipeline"
```

---

### Task 6: Launch with the managed JRE binary

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/LauncherSettings.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftLauncher.kt`

**Interfaces:**
- Consumes: `JreManager.instance.resolveJavaBinary(code)`; `WindowsPathHelper.isAscii`/`toShortPath`.
- Produces: `LauncherSettings(settings, additionalJavaArguments, javaLocation: File?)` whose `getJavaLocation()` returns the injected (Windows-short-pathed) `javaLocation` instead of reading `settings.javaLocation`.

- [ ] **Step 1: Confirm LauncherSettings has a single construction site**

Run: `grep -rn "LauncherSettings(" core desktop`
Expected: exactly one construction, in `MinecraftLauncher.kt` (the class declaration line aside). If others exist, update them in Step 3.

- [ ] **Step 2: Add the injected java location to LauncherSettings**

In `LauncherSettings.kt`, add the constructor parameter:

```kotlin
class LauncherSettings(
    val settings: Settings,
    private val additionalJavaArguments: List<String> = listOf(),
    private val javaLocation: File? = null,
) : ILaunchSettings {
```

Replace `getJavaLocation()` so it uses the injected `javaLocation` (no longer `settings.javaLocation`):

```kotlin
    override fun getJavaLocation(): File? {
        val javaFile = javaLocation ?: return null
        // On Windows, use the JRE's 8.3 short (ASCII) path when it contains non-ASCII characters, so
        // javaw.exe can load jvm.dll despite a Cyrillic install path (JDK-8195129). No-op otherwise.
        val isWindows = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS
        return if (isWindows && !WindowsPathHelper.isAscii(javaFile.absolutePath)) {
            WindowsPathHelper.toShortPath(javaFile)
        } else {
            javaFile
        }
    }
```

(`java.io.File` is already imported in this file.)

- [ ] **Step 3: Resolve and inject the binary in MinecraftLauncher**

In `MinecraftLauncher.kt`, add the import:

```kotlin
import ru.lionzxy.tplauncher.minecraft.jre.JreManager
```

In `launch(...)`, just before building `launchCommands`, resolve the binary:

```kotlin
        val javaFile = JreManager.instance.resolveJavaBinary(minecraft.modpack.javaCode)
            ?: throw IllegalStateException(
                "Managed JRE '${minecraft.modpack.javaCode}' is not installed"
            )
```

Pass it into `LauncherSettings`:

```kotlin
                LauncherSettings(
                    ConfigHelper.config.settings,
                    additionalJavaArguments,
                    javaFile
                ),
```

- [ ] **Step 4: Verify the module compiles and tests pass**

Run: `./gradlew :core:test`
Expected: PASS (no regressions; `Settings.javaLocation` still exists and is still tested at this point).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/LauncherSettings.kt \
        core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftLauncher.kt
git commit -m "Launch Minecraft with the resolved managed JRE binary"
```

---

### Task 7: Remove the jrepath.txt mechanism and the manual Java-path setting

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/ConfigHelper.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/config/SettingsDefault.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/config/Settings.kt`
- Modify: `core/src/test/kotlin/ru/lionzxy/tplauncher/config/SettingsGsonTest.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModel.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsWindow.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt`
- Modify: `desktop/src/test/kotlin/ru/lionzxy/tplauncher/snapshot/SettingsSnapshotTest.kt`

**Interfaces:**
- Removes: `ConfigHelper.getJREPathFile()`, `ConfigHelper.writeJREConfig(path)`, `SettingsDefault.getDefaultJavaLocation()`, `Settings.javaLocation` (property + backing field), `SettingsViewModel.javaPath` / `onJavaPathChange`, `Strings.javaPath`.

- [ ] **Step 1: Remove the jrepath helpers from ConfigHelper**

In `ConfigHelper.kt`, delete `getJREPathFile()` and `writeJREConfig(path)` entirely. Keep `getJavaDirectory()`, `getJreInstallDirectory(code)`, and `getJreManifestCacheFile()`.

- [ ] **Step 2: Remove getDefaultJavaLocation from SettingsDefault**

In `SettingsDefault.kt`, delete the entire `getDefaultJavaLocation()` function. Then remove the now-unused imports `ru.lionzxy.tplauncher.utils.ConfigHelper` and `java.io.File` (the remaining functions use `OperatingSystem`, `Arch`, `SystemMemoryHelper`, `Logger`, `Runtime`).

- [ ] **Step 3: Remove javaLocation from Settings**

In `Settings.kt`, delete:
- the `@SerializedName("javaLocation")` annotation and `private var javaLocationField: String? = null` (lines ~26-27);
- the copy-constructor line `javaLocationField = other.javaLocationField`;
- the entire `var javaLocation: String?` property (getter + setter, lines ~81-90).

- [ ] **Step 4: Update SettingsGsonTest**

In `SettingsGsonTest.kt`:
- In `serializesWithLegacyJsonKeys`, remove the `javaLocation = "/jre/bin/java"` line from the builder and remove the `assertTrue(json, json.contains("\"javaLocation\""))` assertion.
- In `deserializesLegacyJson`, keep the legacy JSON string exactly as-is (it still contains `"javaLocation":"/j"`, which now proves Gson silently ignores the obsolete key) but delete the `assertEquals("/j", s.javaLocation)` assertion.

The two methods become:

```kotlin
    @Test
    fun serializesWithLegacyJsonKeys() {
        val s = Settings().apply {
            heapSize = "3G"
            customJavaParameter = "-Xmx3G"
            commandPrefix = "pfx"
            isDebug = true
            isAutoLoginMinecraft = false
        }
        val json = Gson().toJson(s)

        assertTrue(json, json.contains("\"heapSize\""))
        assertTrue(json, json.contains("\"customJavaParameter\""))
        assertTrue(json, json.contains("\"commandPrefix\""))
        assertTrue(json, json.contains("\"autoLoginMinecraft\""))
        assertTrue(json, json.contains("\"isDebug\""))
        // Private backing-field names must NOT leak into the JSON.
        assertFalse(json, json.contains("Field"))
    }

    @Test
    fun deserializesLegacyJson() {
        // The obsolete "javaLocation" key must be ignored without error on old on-disk configs.
        val legacy = """{"heapSize":"2G","customJavaParameter":"-Xss512k",""" +
            """"commandPrefix":"p","javaLocation":"/j","autoLoginMinecraft":false,"isDebug":true}"""
        val s = Gson().fromJson(legacy, Settings::class.java)

        assertEquals("2G", s.heapSize)
        assertEquals("-Xss512k", s.customJavaParameter)
        assertEquals("p", s.commandPrefix)
        assertFalse(s.isAutoLoginMinecraft)
        assertTrue(s.isDebug)
    }
```

- [ ] **Step 5: Remove the Java-path field from SettingsViewModel**

In `SettingsViewModel.kt`:
- delete `var javaPath by mutableStateOf(settings.javaLocation ?: "")` (and its `private set`);
- delete `fun onJavaPathChange(s: String) { javaPath = s }`;
- delete the line `settings.javaLocation = javaPath.ifBlank { null }` in `apply()`.

In `wipe()`, delete the line:

```kotlin
            if (file.absolutePath == ConfigHelper.getJREPathFile().absolutePath) return@forEach
```

(keep the `getJavaDirectory()` exclusion — it now also preserves the manifest cache).

- [ ] **Step 6: Remove the Java-path row from SettingsWindow**

In `SettingsWindow.kt`, delete the entire `TpField` block for the Java path (the block starting `TpField(label = Strings.javaPath, ...)` through its closing brace — the prefix field's block stays, the debug checkbox row stays):

```kotlin
                TpField(label = Strings.javaPath, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.javaPath,
                        onValueChange = vm::onJavaPathChange,
                    )
                }
```

- [ ] **Step 7: Remove the Strings entry**

In `Strings.kt`, delete the line:

```kotlin
    const val javaPath = "Путь до Java"
```

- [ ] **Step 8: Update SettingsSnapshotTest**

In `SettingsSnapshotTest.kt`, delete the line `settings.javaLocation = "C:\\Program Files\\Java\\java.exe"` and the `*   javaPath = "C:\\Program Files\\Java\\java.exe"` comment line in the KDoc.

- [ ] **Step 9: Verify everything compiles and all tests pass**

Run: `./gradlew test`
Expected: PASS (core + desktop). The settings snapshot test still passes (it only asserts the PNG is non-empty).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Remove jrepath.txt mechanism and manual Java-path setting"
```

---

### Task 8: Full verification and cleanup guard

**Files:** none (verification only)

- [ ] **Step 1: Confirm no stale references remain**

Run: `grep -rn "javaLocation\|jrepath\|getJREPathFile\|writeJREConfig\|getDefaultJavaLocation\|onJavaPathChange\|Strings.javaPath" core desktop`
Expected: **no matches** (the only acceptable matches would be inside `docs/`, which is not searched here).

- [ ] **Step 2: Clean build with the full test suite**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — all core and desktop tests pass, both modules compile.

- [ ] **Step 3: Mark the spec implemented**

In `docs/superpowers/specs/2026-06-27-per-modpack-managed-jre-design.md`, change the header `**Status:**` line to `Implemented`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-27-per-modpack-managed-jre-design.md
git commit -m "Mark managed-JRE spec implemented"
```

---

## Self-Review

**1. Spec coverage:**
- Manifest model + base64 hash keys → Task 1. Selection by OS/arch → Task 1. ✓
- `JRES_JSON_LINK` + `MinecraftModpack.javaCode` default `jre8` → Task 2. ✓
- Fetch + disk cache + offline fallback + hard-fail-when-absent → Task 3 (fetch/cache) and Task 4 (`ensureInstalled` throws; reuse-when-installed). ✓
- Install (download→verify archive→extract→verify binary→+x on POSIX) + Windows `javaw.exe` + resolve → Task 4. ✓
- Pipeline integration (first, hard-fail propagates) → Task 5. ✓
- Launch integration (`LauncherSettings` injected file, Windows short-path) → Task 6. ✓
- Remove jrepath.txt + manual field + `Settings.javaLocation` + UI + Strings + test updates; `wipe()` preserves JRE dir → Task 7. ✓
- Install/cache layout under `technomine/jre/` → `ConfigHelper.getJreInstallDirectory` / `getJreManifestCacheFile` (Task 4). ✓
- Testing (selection, parse, install-decision, offline behavior, updated Settings tests) → Tasks 1, 3, 4, 7. ✓

**2. Placeholder scan:** No TBD/TODO/"handle errors"/"similar to" — every code step contains complete code. ✓

**3. Type consistency:** `parseJreManifest`, `findByCode`, `selectFile(osName, archAliases)`, `isJavaBinaryValid(binary, expectedBase64Sha)`, `fetchJreManifest(http, url, cacheFile)`, `JrePlatform(osName, archAliases, isWindows)`, `extractArchive(archive, extension, dest)`, `JreManager(http, manifestUrl, manifestCacheFile, installDirFor, platform, extract)`, `ensureInstalled(code, monitor)`, `resolveJavaBinary(code)`, `ConfigHelper.getJreInstallDirectory(code)` / `getJreManifestCacheFile()` are used identically across Tasks 1–6. `JreManager.instance` consumed by Tasks 5–6 matches its definition in Task 4. ✓

**Open implementation note:** `jarchivelib` 1.2.0 API was verified against the cached jar (`ArchiverFactory.createArchiver(ArchiveFormat, CompressionType)` and `Archiver.extract(File, File)`); the real download/extract path is exercised end-to-end only at runtime (the unit tests inject a fake `extract`), so a first real launch on each OS is the integration check for extraction + executable-bit handling.
