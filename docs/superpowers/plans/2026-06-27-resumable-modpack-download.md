# Resilient, Resumable Modpack Downloads — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make modpack downloads resumable and self-verifying (per-changed-file SHA-256 skip/verify), user-tunable in parallelism, robust to flaky networks (in-run retry), and cheap to poll (conditional GET + gzip) — all backward compatible with old clients and the existing server format.

**Architecture:** The server (`update-maker.py`) keeps its MD5 index and `{ts:{path:0|1}}` changelog buckets unchanged and adds an OPTIONAL flat top-level `"sha256": {relpath: hex}` map carrying hashes for changed files only. The client parses that map (ignoring it when absent), skips/verifies files that have a hash, retries failures in-run, reads a configurable parallelism setting, and fetches the changelog with `If-None-Match` (304) + gzip. Files without a hash are downloaded without a check (base files are validated by the initial full-zip install).

**Tech Stack:** Kotlin (core = plain JVM + ktor CIO client + Gson; desktop = Compose Desktop), JUnit4 + ktor MockEngine, commons-codec; Python 3 (server `update-maker.py`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-27-resumable-modpack-download-design.md` (authoritative).
- Branch/worktree: `feature/resumable-modpack-download` at `/Users/lionzxy/private/TechnoparkLauncher-resume` (off `master`). Commit `40ef16c` already landed the semaphore concurrency fix (`mapWithBoundedConcurrency`).
- **Backward compatibility is non-negotiable:** never change the changelog bucket value shape (`0|1`) or the existing MD5 index. New data is additive and optional.
- **No migration:** the server diff key stays MD5; do not switch the index hash.
- Hash algorithm/encoding: **SHA-256, lowercase hex**.
- Parallelism default: `Runtime.getRuntime().availableProcessors()` clamped to `1..32`; the value used by `mapWithBoundedConcurrency` must always be `>= 1`.
- A file with no expected hash is downloaded without skip or verify (do NOT block or warn).
- Kotlin test run: `./gradlew :core:test --tests "<FQCN>"` (core) / `./gradlew :desktop:test --tests "<FQCN>"` (desktop). Server check is a Python script run on a temp dir.
- Commit after every task with the trailer line `Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D`.

---

### Task 1: `File.sha256Hex()` helper

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/Extensions.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/utils/ExtensionsTest.kt` (create)

**Interfaces:**
- Produces: `fun File.sha256Hex(): String` — lowercase hex SHA-256 of the file's bytes (used by Tasks 6 & 7).

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/ru/lionzxy/tplauncher/utils/ExtensionsTest.kt`:
```kotlin
package ru.lionzxy.tplauncher.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ExtensionsTest {
    @Test
    fun sha256HexMatchesKnownVector() {
        val f = Files.createTempFile("sha", ".bin").toFile()
        f.writeText("abc")
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            f.sha256Hex(),
        )
    }

    @Test
    fun sha256HexOfEmptyFile() {
        val f = Files.createTempFile("sha", ".bin").toFile()
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            f.sha256Hex(),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.utils.ExtensionsTest"`
Expected: FAIL — `Unresolved reference: sha256Hex`.

- [ ] **Step 3: Implement the helper**

In `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/Extensions.kt`, add next to `hashSHA1` (it already imports `org.apache.commons.codec.digest.DigestUtils`, `java.io.FileInputStream`, `java.io.InputStream`):
```kotlin
fun File.sha256Hex(): String {
    return DigestUtils.sha256Hex(FileInputStream(this) as InputStream)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.utils.ExtensionsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/utils/Extensions.kt core/src/test/kotlin/ru/lionzxy/tplauncher/utils/ExtensionsTest.kt
git commit -m "feat(utils): add File.sha256Hex() helper

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 2: `parallelDownloads` setting (model + defaults)

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/config/SettingsDefault.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/config/Settings.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/config/SettingsGsonTest.kt` (modify)

**Interfaces:**
- Produces: `SettingsDefault.getDefaultParallelDownloads(): Int` (= `availableProcessors().coerceIn(1,32)`); `Settings.parallelDownloads: Int` (lazy default, clamp-on-write `1..32`, JSON key `parallelDownloads`). Consumed by Tasks 3 & 4 & 7.

- [ ] **Step 1: Write the failing tests**

Append to `core/src/test/kotlin/ru/lionzxy/tplauncher/config/SettingsGsonTest.kt` (inside the class), mirroring the existing `enableLogView_*` tests:
```kotlin
    @Test
    fun parallelDownloads_defaultsToClampedCoreCount_andRoundTrips() {
        val expected = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        assertEquals(expected, Settings().parallelDownloads)
        val json = Gson().toJson(Settings().apply { parallelDownloads = 5 })
        assertTrue(json, json.contains("\"parallelDownloads\""))
        assertEquals(5, Gson().fromJson(json, Settings::class.java).parallelDownloads)
    }

    @Test
    fun parallelDownloads_absentFromLegacyJson_usesDefault() {
        val expected = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        val legacy = """{"heapSize":"2G","isDebug":false}"""
        assertEquals(expected, Gson().fromJson(legacy, Settings::class.java).parallelDownloads)
    }

    @Test
    fun parallelDownloads_isClampedOnWrite() {
        assertEquals(32, Settings().apply { parallelDownloads = 9999 }.parallelDownloads)
        assertEquals(1, Settings().apply { parallelDownloads = 0 }.parallelDownloads)
    }
```
(`assertEquals`, `assertTrue` are already imported in this file; add `import com.google.gson.Gson` if not present.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.config.SettingsGsonTest"`
Expected: FAIL — `Unresolved reference: parallelDownloads`.

- [ ] **Step 3: Add the default helper**

In `core/src/main/kotlin/ru/lionzxy/tplauncher/config/SettingsDefault.kt`, add to the `object SettingsDefault`:
```kotlin
    fun getDefaultParallelDownloads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
```

- [ ] **Step 4: Add the setting property**

In `core/src/main/kotlin/ru/lionzxy/tplauncher/config/Settings.kt`:

Add the backing field after `var enableLogView: Boolean = false` (line 34):
```kotlin
    // 0 = "unset" sentinel: missing/old configs and invalid values fall back to the
    // CPU-core default via the getter, mirroring heapSize's lazy default.
    @SerializedName("parallelDownloads")
    private var parallelDownloadsField: Int = 0
```

Add to the copy constructor (after `enableLogView = other.enableLogView`, line 44):
```kotlin
        parallelDownloadsField = other.parallelDownloadsField
```

Add the public property (e.g. after the `heapSize` property):
```kotlin
    var parallelDownloads: Int
        get() {
            if (parallelDownloadsField <= 0) {
                parallelDownloadsField = SettingsDefault.getDefaultParallelDownloads()
            }
            return parallelDownloadsField
        }
        set(value) {
            parallelDownloadsField = value.coerceIn(1, 32)
        }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.config.SettingsGsonTest"`
Expected: PASS (all, including the existing tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/config/Settings.kt core/src/main/kotlin/ru/lionzxy/tplauncher/config/SettingsDefault.kt core/src/test/kotlin/ru/lionzxy/tplauncher/config/SettingsGsonTest.kt
git commit -m "feat(config): add parallelDownloads setting (default = core count, clamped 1..32)

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 3: Drive download concurrency from the setting

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloader.kt`

**Interfaces:**
- Consumes: `Settings.parallelDownloads` (Task 2), `mapWithBoundedConcurrency` (already present).
- Produces: nothing new; behavior change only.

- [ ] **Step 1: Replace the constant with the setting**

In `IncrementalDownloader.kt`, the `download()` body currently calls
`mapWithBoundedConcurrency(toDownload, DOWNLOAD_PARALLELISM) { ... }`. Change the read to the setting:
```kotlin
        val parallelism = ConfigHelper.config.settings.parallelDownloads
        val downloadedFiles = AtomicInteger(0)
        val failures = runBlocking {
            // Bound the truly-concurrent downloads (see mapWithBoundedConcurrency): a limited
            // dispatcher does NOT cap suspending network I/O, so the old code fired every file at
            // the host at once and the connection storm produced widespread connect timeouts.
            mapWithBoundedConcurrency(toDownload, parallelism) { (key, file) ->
                Logger.d(LOG_TAG, "Downloading $key")
                val url = UriEncodeUtils.encodePath(joinUrl(host, key), Charsets.UTF_8)
                HttpDownloader.instance.downloadToFile(url, file)
                val done = downloadedFiles.incrementAndGet()
                progressMutex.withLock {
                    minecraft.progressMonitor.setStatus("Загружено $done/${toDownload.size}")
                    minecraft.progressMonitor.setProgress(done)
                }
            }.map { (item, error) -> item.first to error }
        }
```

Then delete the now-unused companion constant block:
```kotlin
    private companion object {
        // Keep concurrent connections modest: ...
        const val DOWNLOAD_PARALLELISM = 8
    }
```
(`ConfigHelper` is already imported in this file.)

- [ ] **Step 2: Verify it compiles and existing tests pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: PASS (no behavior tested here directly; this confirms the module still compiles and the pure-logic tests pass). Also run `./gradlew :core:compileKotlin` to be sure the constant removal left no references.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloader.kt
git commit -m "feat(downloader): read parallelism from settings instead of a constant

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 4: Settings UI for parallel downloads

**Files:**
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModel.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsWindow.kt`
- Test: `desktop/src/test/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModelTest.kt` (modify; mirror the existing `enableLogView` round-trip test)

**Interfaces:**
- Consumes: `Settings.parallelDownloads` (Task 2).
- Produces: VM field `parallelDownloads: String`, callback `onParallelDownloadsChange(String)`, persisted on `apply()`.

- [ ] **Step 1: Write the failing VM test**

In `desktop/src/test/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModelTest.kt`, add a test mirroring the existing `enableLogView` round-trip test (use the same construction/persist pattern already in that file):
```kotlin
    @Test
    fun parallelDownloads_editAndApply_persistsClampedInt() {
        val settings = Settings().apply { parallelDownloads = 4 }
        var saved: Settings? = null
        val vm = SettingsViewModel(
            settings = settings,
            persist = { saved = it },
            onClose = {},
            backupSizeProvider = { "x" },
        )
        vm.onParallelDownloadsChange("12")
        vm.apply()
        assertEquals(12, saved!!.parallelDownloads)
    }

    @Test
    fun parallelDownloads_nonNumericFallsBackToDefault() {
        val settings = Settings()
        var saved: Settings? = null
        val vm = SettingsViewModel(settings, { saved = it }, {}, {}, { "x" })
        vm.onParallelDownloadsChange("")   // cleared field
        vm.apply()
        assertEquals(SettingsDefault.getDefaultParallelDownloads(), saved!!.parallelDownloads)
    }
```
Add imports if missing: `import ru.lionzxy.tplauncher.config.Settings`, `import ru.lionzxy.tplauncher.config.SettingsDefault`, `import org.junit.Assert.assertEquals`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.ui.settings.SettingsViewModelTest"`
Expected: FAIL — `Unresolved reference: onParallelDownloadsChange`.

- [ ] **Step 3: Add the Russian label**

In `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt`, add after `uiScale` (line 27):
```kotlin
    const val parallelDownloads = "Параллельных загрузок"
```

- [ ] **Step 4: Add VM state, callback, and apply write-back**

In `SettingsViewModel.kt`:

State (after `enableLogView` state at line 64-65):
```kotlin
    var parallelDownloads by mutableStateOf(settings.parallelDownloads.toString())
        private set
```
Callback (after `onEnableLogViewChange`, line 89):
```kotlin
    fun onParallelDownloadsChange(s: String) { parallelDownloads = s.filter(Char::isDigit) }
```
Write-back in `apply()` (after `settings.enableLogView = enableLogView`, line 112) — tolerant parse + fall back to default (do not throw; keep the documented save-bug behavior of not early-returning):
```kotlin
        settings.parallelDownloads =
            parallelDownloads.toIntOrNull() ?: SettingsDefault.getDefaultParallelDownloads()
```
Add import: `import ru.lionzxy.tplauncher.config.SettingsDefault`.

(Note: `Settings.parallelDownloads`'s setter clamps to `1..32`, so a parsed `12` stays `12` and a parsed `9999` becomes `32`.)

- [ ] **Step 5: Run the VM test to verify it passes**

Run: `./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.ui.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Add the UI row**

In `SettingsWindow.kt`, add a row inside the form `Column` after the UI-scale `TpField` block (after line 158, before the closing `}` at 159):
```kotlin
                TpField(label = Strings.parallelDownloads, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.parallelDownloads,
                        onValueChange = vm::onParallelDownloadsChange,
                    )
                }
```

- [ ] **Step 7: Verify desktop compiles**

Run: `./gradlew :desktop:compileKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModel.kt desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsWindow.kt desktop/src/test/kotlin/ru/lionzxy/tplauncher/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(ui): settings field to control parallel downloads

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 5: Backward-compatible changelog parse with optional `sha256` map

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogic.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogicTest.kt`

**Interfaces:**
- Produces: `ParsedChangeLog` gains `val hashes: Map<String, String>` (relpath → hex; empty when the changelog has no `"sha256"` key). `parseChangeLog(json, lastUpdate)` keeps its signature and return type name, now also populating `hashes`. Consumed by Tasks 6 & 7.

- [ ] **Step 1: Write the failing tests**

Append to `IncrementalDownloadLogicTest.kt`:
```kotlin
    // ---- changelog "sha256" optional map ----

    @Test
    fun parseChangeLogReadsOptionalSha256Map() {
        val json = """
            {"100": {"a.txt": "1", "old.txt": "0"},
             "sha256": {"a.txt": "deadbeef"}}
        """.trimIndent()
        val parsed = parseChangeLog(json, lastUpdate = 0)
        assertEquals(100L, parsed.lastTimestamp)
        assertEquals(Action.ADD, parsed.changes["a.txt"])
        assertEquals(Action.REMOVE, parsed.changes["old.txt"])
        assertEquals("deadbeef", parsed.hashes["a.txt"])
        // the "sha256" key is NOT treated as a timestamp bucket
        assertNull(parsed.changes["sha256"])
    }

    @Test
    fun parseChangeLogWithNoSha256MapYieldsEmptyHashes() {
        val parsed = parseChangeLog("""{"100": {"a.txt": "1"}}""", lastUpdate = 0)
        assertEquals(Action.ADD, parsed.changes["a.txt"])
        assertTrue(parsed.hashes.isEmpty())
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: FAIL — `hashes` unresolved / `parseChangeLog` returns old shape.

- [ ] **Step 3: Implement the parse**

In `IncrementalDownloadLogic.kt`, replace the `ParsedChangeLog` data class, the `changeLogType`, and `parseChangeLog` with a `JsonObject`-walking parse that pulls the optional `"sha256"` key out before reading buckets:
```kotlin
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Result of interpreting a server changelog against the locally-applied timestamp. */
internal data class ParsedChangeLog(
    /** Merged per-path actions for every changelog bucket newer than the local timestamp. */
    val changes: Map<String, Action>,
    /** Highest changelog timestamp seen (0 when nothing newer than [lastUpdate] exists). */
    val lastTimestamp: Long,
    /** Optional server-provided SHA-256 (relpath -> lowercase hex) for changed files; empty if absent. */
    val hashes: Map<String, String> = emptyMap(),
)

private fun parseAction(code: String): Action? = when (code) {
    "0" -> Action.REMOVE
    "1" -> Action.ADD
    else -> null
}

/**
 * Parses the changelog. Numeric top-level keys are timestamp buckets (`path -> "0"|"1"`); the
 * optional non-numeric `"sha256"` key is a flat `path -> hex` map of hashes for changed files.
 * Only buckets newer than [lastUpdate] are kept and merged in ascending order (later wins per path).
 * Unknown/non-numeric keys other than `"sha256"`, and unparseable values, are skipped (not fatal).
 */
internal fun parseChangeLog(json: String, lastUpdate: Long): ParsedChangeLog {
    val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
        ?: return ParsedChangeLog(emptyMap(), 0L)

    val hashes = LinkedHashMap<String, String>()
    root.get("sha256")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (path, v) ->
        if (v.isJsonPrimitive) hashes[path] = v.asString
    }

    val buckets = root.entrySet()
        .mapNotNull { (key, value) ->
            val ts = key.toLongOrNull() ?: return@mapNotNull null
            if (!value.isJsonObject) return@mapNotNull null
            ts to value.asJsonObject
        }
        .filter { (ts, _) -> ts > lastUpdate }
        .sortedBy { (ts, _) -> ts }

    val merged = LinkedHashMap<String, Action>()
    buckets.forEach { (_, ops) ->
        ops.entrySet().forEach { (path, code) ->
            if (code.isJsonPrimitive) parseAction(code.asString)?.let { merged[path] = it }
        }
    }
    return ParsedChangeLog(
        changes = merged,
        lastTimestamp = buckets.lastOrNull()?.first ?: 0L,
        hashes = hashes,
    )
}
```
Remove the now-unused `private val changeLogGson = Gson()` and `changeLogType` declarations and the `com.google.gson.Gson` / `com.google.gson.reflect.TypeToken` imports if no longer referenced.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: PASS (all, including the pre-existing `parseChangeLog*` tests — the new parser is behavior-compatible for numeric buckets).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogic.kt core/src/test/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogicTest.kt
git commit -m "feat(downloader): parse optional sha256 map from changelog (backward compatible)

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 6: `filterUpToDate` — the parallel hash-skip step

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogic.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogicTest.kt`

**Interfaces:**
- Consumes: `File.sha256Hex()` (Task 1), `mapWithBoundedConcurrency` (present), `ParsedChangeLog.hashes` (Task 5).
- Produces:
  `suspend fun filterUpToDate(toDownload: List<Pair<String, File>>, expectedHashes: Map<String, String>, parallelism: Int, onChecked: () -> Unit = {}): List<Pair<String, File>>` — returns the subset that still needs downloading. An entry is dropped only when `expectedHashes[key]` exists, the file exists, and its `sha256Hex()` equals the expected hash. Consumed by Task 7.

- [ ] **Step 1: Write the failing tests**

Append to `IncrementalDownloadLogicTest.kt` (imports `kotlinx.coroutines.runBlocking`, `java.io.File`, `java.nio.file.Files` are already present/added in Task earlier):
```kotlin
    // ---- filterUpToDate: hash-skip ----

    private fun writeFile(dir: File, name: String, content: String): File {
        val f = File(dir, name); f.parentFile.mkdirs(); f.writeText(content); return f
    }

    @Test
    fun filterUpToDateSkipsMatching_keepsMismatchedMissingAndUnhashed() = runBlocking {
        val dir = tempBase()
        val match = writeFile(dir, "match.txt", "same")
        val mism = writeFile(dir, "mismatch.txt", "local-different")
        val expected = mapOf(
            "match.txt" to match.sha256Hex(),          // present + matches  -> skip
            "mismatch.txt" to "0000",                  // present + differs  -> keep
            "missing.txt" to "abcd",                   // absent             -> keep
            // "nohash.txt" has no expected hash        // unhashed           -> keep
        )
        val toDownload = listOf(
            "match.txt" to File(dir, "match.txt"),
            "mismatch.txt" to File(dir, "mismatch.txt"),
            "missing.txt" to File(dir, "missing.txt"),
            "nohash.txt" to File(dir, "nohash.txt"),
        )
        val remaining = filterUpToDate(toDownload, expected, parallelism = 4)
        assertEquals(
            setOf("mismatch.txt", "missing.txt", "nohash.txt"),
            remaining.map { it.first }.toSet(),
        )
    }
```
(`match.sha256Hex()` requires `import ru.lionzxy.tplauncher.utils.sha256Hex`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: FAIL — `Unresolved reference: filterUpToDate`.

- [ ] **Step 3: Implement `filterUpToDate`**

Add to `IncrementalDownloadLogic.kt` (it already imports the coroutine helpers used by `mapWithBoundedConcurrency`; add `import ru.lionzxy.tplauncher.utils.sha256Hex`):
```kotlin
/**
 * Drops entries from [toDownload] that are already correct on disk: an entry is removed only when
 * [expectedHashes] has its key, the local file exists, and its SHA-256 matches. Entries with no
 * expected hash (base files validated by the initial install, or old-format packs) are KEPT and
 * never hashed. Hashing runs with at most [parallelism] in flight; [onChecked] fires once per item.
 */
internal suspend fun filterUpToDate(
    toDownload: List<Pair<String, File>>,
    expectedHashes: Map<String, String>,
    parallelism: Int,
    onChecked: () -> Unit = {},
): List<Pair<String, File>> {
    val keep = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    mapWithBoundedConcurrency(toDownload, parallelism) { (key, file) ->
        val expected = expectedHashes[key]
        // A read error while hashing means "couldn't verify" -> keep (re-download).
        val upToDate = expected != null && file.isFile &&
            runCatching { file.sha256Hex().equals(expected, ignoreCase = true) }.getOrDefault(false)
        if (!upToDate) keep.add(key)
        onChecked()
    }
    // Preserve input order; keep only the not-up-to-date entries.
    return toDownload.filter { it.first in keep }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogic.kt core/src/test/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogicTest.kt
git commit -m "feat(downloader): filterUpToDate parallel hash-skip (resume)

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 7: In-run retry helper + wire skip & verify into `download()`

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogic.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloader.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloadLogicTest.kt`

**Interfaces:**
- Consumes: `mapWithBoundedConcurrency`, `filterUpToDate` (Task 6), `ParsedChangeLog.hashes` (Task 5), `File.sha256Hex()` (Task 1).
- Produces:
  `suspend fun <T> downloadWithRetries(items: List<T>, parallelism: Int, maxAttempts: Int, block: suspend (T) -> Unit): List<Pair<T, Throwable>>` — runs `block` over `items` with bounded concurrency, retrying only the failed subset up to `maxAttempts` total passes; returns the still-failing items with their last error. Used by `IncrementalDownloader.download()`.

- [ ] **Step 1: Write the failing tests for `downloadWithRetries`**

Append to `IncrementalDownloadLogicTest.kt`:
```kotlin
    // ---- downloadWithRetries: bounded concurrency + retry the failed subset ----

    @Test
    fun downloadWithRetries_recoversTransientFailureWithinAttempts() = runBlocking {
        val attemptsByItem = HashMap<Int, Int>()
        val failures = downloadWithRetries(
            items = (1..5).toList(), parallelism = 2, maxAttempts = 3,
        ) { n ->
            val a = (attemptsByItem[n] ?: 0) + 1
            attemptsByItem[n] = a
            if (n == 3 && a < 2) throw RuntimeException("transient $n") // succeeds on 2nd pass
        }
        assertTrue("transient item should recover", failures.isEmpty())
        assertEquals(2, attemptsByItem[3])
    }

    @Test
    fun downloadWithRetries_reportsPersistentFailureAfterAllAttempts() = runBlocking {
        var calls = 0
        val failures = downloadWithRetries(
            items = listOf(7), parallelism = 1, maxAttempts = 3,
        ) { calls++; throw RuntimeException("always") }
        assertEquals(3, calls)                       // tried maxAttempts times
        assertEquals(listOf(7), failures.map { it.first })
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: FAIL — `Unresolved reference: downloadWithRetries`.

- [ ] **Step 3: Implement `downloadWithRetries`**

Add to `IncrementalDownloadLogic.kt`:
```kotlin
/**
 * Runs [block] over [items] with at most [parallelism] in flight; after each pass, the items that
 * threw are retried, up to [maxAttempts] total passes. Returns the items still failing after the
 * last pass paired with their most recent error. A successful item is never retried (so callers can
 * rely on hash-skip to avoid redundant work). [key] is only used to de-duplicate/identify items.
 */
internal suspend fun <T> downloadWithRetries(
    items: List<T>,
    parallelism: Int,
    maxAttempts: Int,
    block: suspend (T) -> Unit,
): List<Pair<T, Throwable>> {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
    var pending = items
    var lastFailures: List<Pair<T, Throwable>> = emptyList()
    var attempt = 0
    while (attempt < maxAttempts && pending.isNotEmpty()) {
        attempt++
        lastFailures = mapWithBoundedConcurrency(pending, parallelism) { block(it) }
        pending = lastFailures.map { it.first }
    }
    return lastFailures
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"`
Expected: PASS.

- [ ] **Step 5: Wire skip + verify + retry into `download()`**

In `IncrementalDownloader.kt`, the abstract base stores parsed changelog. First store the hashes alongside `changes`. Add a field next to `changes`/`lastChangeTimestamp`:
```kotlin
    private var changeHashes: Map<String, String> = emptyMap()
```
In `init()`, after `lastChangeTimestamp = parsed.lastTimestamp`, add:
```kotlin
            changeHashes = parsed.hashes
```
Then replace the download block (the `parallelism`/`runBlocking { mapWithBoundedConcurrency ... }` section from Task 3) with skip → retry → verify:
```kotlin
        val parallelism = ConfigHelper.config.settings.parallelDownloads

        // Resume: drop files already correct on disk (verified by the server's SHA-256). Files with
        // no expected hash are kept and downloaded as before (base files are validated at install).
        minecraft.progressMonitor.setStatus("Проверка файлов...")
        val pending = runBlocking { filterUpToDate(toDownload, changeHashes, parallelism) }
        val skipped = toDownload.size - pending.size
        if (skipped > 0) {
            Logger.i(LOG_TAG, "Skipped $skipped already-current file(s) for '${info.key}'")
        }
        if (pending.isEmpty()) {
            persistTimestamp(info)
            return
        }

        Logger.i(LOG_TAG, "Downloading ${pending.size} file(s) for '${info.key}'")
        minecraft.progressMonitor.setStatus("Загружаем модпак...")
        minecraft.progressMonitor.setMax(pending.size)
        minecraft.progressMonitor.setProgress(0)

        val downloadedFiles = AtomicInteger(0)
        val failures = runBlocking {
            downloadWithRetries(
                items = pending,
                parallelism = parallelism,
                maxAttempts = DOWNLOAD_ATTEMPTS,
            ) { (k, file) ->
                Logger.d(LOG_TAG, "Downloading $k")
                val url = UriEncodeUtils.encodePath(joinUrl(host, k), Charsets.UTF_8)
                HttpDownloader.instance.downloadToFile(url, file)
                changeHashes[k]?.let { expected ->
                    val actual = file.sha256Hex()
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw IOException("hash mismatch for $k: expected $expected got $actual")
                    }
                }
                val done = downloadedFiles.incrementAndGet()
                progressMutex.withLock {
                    minecraft.progressMonitor.setStatus("Загружено $done/${pending.size}")
                    minecraft.progressMonitor.setProgress(done)
                }
            }.map { (item, error) -> item.first to error }
        }
```
Move the `Logger.i("Downloading N file(s)...")`/`setMax`/`setProgress(0)` that previously used `toDownload` to use `pending` as shown (the earlier lines 97-100 block is replaced by the block above; delete the old duplicate `Logger.i("Downloading ${toDownload.size}...")` + setMax(toDownload.size) lines so they aren't emitted twice).

Add the attempts constant to the companion (re-introduce a small companion since Task 3 removed it):
```kotlin
    private companion object {
        // 1 initial pass + 2 retries of the still-failing subset (hash-skip keeps retries cheap).
        const val DOWNLOAD_ATTEMPTS = 3
    }
```
Imports already present: `kotlinx.coroutines.runBlocking`, `java.io.IOException`, `java.util.concurrent.atomic.AtomicInteger`. Add `import ru.lionzxy.tplauncher.utils.sha256Hex`. `filterUpToDate`, `downloadWithRetries` are in the same package (no import needed).

- [ ] **Step 6: Verify the module compiles and pure-logic tests pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.prepare.downloader.base.IncrementalDownloadLogicTest"` then `./gradlew :core:compileKotlin`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/
git commit -m "feat(downloader): hash-skip resume, post-download verify, 2-pass in-run retry

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 8: Changelog fetch — gzip + conditional GET (304)

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/HttpClient.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/config/DownloadedInfo.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloader.kt`
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/utils/ConfigHelper.kt` (add a cache-dir accessor)
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/utils/HttpDownloaderTest.kt`

**Interfaces:**
- Produces: `data class ConditionalResponse(val notModified: Boolean, val etag: String?, val body: String?)` and `suspend fun HttpDownloader.getStringConditional(url: String, etag: String?): ConditionalResponse`. `DownloadedInfo.changelogEtag: String?`. `ConfigHelper.getCacheDirectory(): File`.

- [ ] **Step 1: Write the failing tests (MockEngine)**

Append to `HttpDownloaderTest.kt` (it already builds a `HttpDownloader` over a `MockEngine` via `applyDefaults()`):
```kotlin
    @Test
    fun getStringConditional_returns200BodyAndEtag() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "BODY",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ETag, "\"abc\""),
            )
        }
        val dl = HttpDownloader(HttpClient(engine) { applyDefaults() })
        val r = dl.getStringConditional("http://x/changelog.json", etag = null)
        assertFalse(r.notModified)
        assertEquals("BODY", r.body)
        assertEquals("\"abc\"", r.etag)
    }

    @Test
    fun getStringConditional_sends304WhenEtagMatches() = runBlocking {
        var sentIfNoneMatch: String? = null
        val engine = MockEngine { req ->
            sentIfNoneMatch = req.headers[HttpHeaders.IfNoneMatch]
            respond(content = "", status = HttpStatusCode.NotModified)
        }
        val dl = HttpDownloader(HttpClient(engine) { applyDefaults() })
        val r = dl.getStringConditional("http://x/changelog.json", etag = "\"abc\"")
        assertTrue(r.notModified)
        assertNull(r.body)
        assertEquals("\"abc\"", sentIfNoneMatch)
    }
```
Add imports as needed: `io.ktor.client.engine.mock.MockEngine`, `io.ktor.client.engine.mock.respond`, `io.ktor.http.HttpStatusCode`, `io.ktor.http.HttpHeaders`, `io.ktor.http.headersOf`, `kotlinx.coroutines.runBlocking`, `org.junit.Assert.*`.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.utils.HttpDownloaderTest"`
Expected: FAIL — `Unresolved reference: getStringConditional` / `ConditionalResponse`. (Note: `expectSuccess = true` makes 304 throw by default — handled in Step 3.)

- [ ] **Step 3: Implement gzip + conditional GET**

In `HttpClient.kt`:

Add gzip to `applyDefaults()` (after the `UserAgent` install):
```kotlin
    install(io.ktor.client.plugins.compression.ContentEncoding) {
        gzip()
        deflate()
    }
```

Add the conditional GET API (top-level data class + method on `HttpDownloader`):
```kotlin
/** Result of a conditional GET: [notModified] true on 304 (use the cache); else [body]+[etag]. */
data class ConditionalResponse(val notModified: Boolean, val etag: String?, val body: String?)
```
Add inside `class HttpDownloader`:
```kotlin
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
```
Add imports to `HttpClient.kt`: `io.ktor.client.request.header`, `io.ktor.http.HttpHeaders`, `io.ktor.http.HttpStatusCode`, `io.ktor.http.isSuccess`, `java.io.IOException`. Ensure the ktor `content-encoding` artifact is available (it ships in `io.ktor:ktor-client-encoding`; if the build fails to resolve `ContentEncoding`, add `implementation("io.ktor:ktor-client-encoding:<same version as other ktor deps>")` to `core/build.gradle.kts` — match the version already used for `ktor-client-cio`).

- [ ] **Step 4: Run the HTTP tests to verify pass**

Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.utils.HttpDownloaderTest"`
Expected: PASS (all, including pre-existing tests).

- [ ] **Step 5: Add `changelogEtag` + cache dir**

In `DownloadedInfo.kt`:
```kotlin
class DownloadedInfo(
    var initFileDownload: Boolean? = false,
    var lastUpdateFromChangeLog: Long? = 0,
    var changelogEtag: String? = null,
)
```
In `ConfigHelper.kt`, add a cache directory accessor next to the other directory helpers (mirror `getDefaultDirectory()`):
```kotlin
    fun getCacheDirectory(): File =
        File(getDefaultDirectory(), "cache").apply { mkdirs() }
```

- [ ] **Step 6: Wire conditional fetch into `init()`**

In `IncrementalDownloader.kt`, replace the body of the `try { ... }` in `init()` (the `val json = runBlocking { HttpDownloader.instance.getString(url) }` line and its parse) with a conditional fetch using the per-pack etag + on-disk body cache:
```kotlin
        val info0 = ConfigHelper.config.modpackDownloadedInfo[info.key]
        val cacheFile = File(ConfigHelper.getCacheDirectory(), "${info.key}_changelog.json")
        try {
            val resp = runBlocking { HttpDownloader.instance.getStringConditional(url, info0?.changelogEtag) }
            val body = if (resp.notModified && cacheFile.isFile) {
                cacheFile.readText()
            } else {
                val fresh = resp.body ?: runBlocking { HttpDownloader.instance.getString(url) }
                runCatching { cacheFile.writeText(fresh) }
                if (resp.etag != null) persistChangelogEtag(info, resp.etag)
                fresh
            }
            val parsed = parseChangeLog(body, lastUpdate)
            changes = parsed.changes
            lastChangeTimestamp = parsed.lastTimestamp
            changeHashes = parsed.hashes
            Logger.i(LOG_TAG, "Update list parsed: ${changes.size} changed file(s) since timestamp $lastUpdate")
            minecraft.progressMonitor.setStatus("Данные обновления получены, применяем их...")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to fetch update list for '${info.key}'", e)
            Sentry.captureException(e)
            minecraft.progressMonitor.setStatus("Не удалось получить обновления, запуск на текущей версии")
        }
```
Add a helper next to `persistTimestamp`:
```kotlin
    private fun persistChangelogEtag(info: IncrementalDownloaderInfo, etag: String) {
        ConfigHelper.writeToConfig {
            val di = modpackDownloadedInfo[info.key] ?: DownloadedInfo()
            di.changelogEtag = etag
            modpackDownloadedInfo[info.key] = di
        }
    }
```
(Note: on a 304 with no cache file present, the code falls through to a plain full `getString` — safe fallback. `DownloadedInfo`, `ConfigHelper`, `File` already imported.)

- [ ] **Step 7: Verify compile + core tests**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/utils/HttpClient.kt core/src/main/kotlin/ru/lionzxy/tplauncher/config/DownloadedInfo.kt core/src/main/kotlin/ru/lionzxy/tplauncher/utils/ConfigHelper.kt core/src/main/kotlin/ru/lionzxy/tplauncher/prepare/downloader/base/IncrementalDownloader.kt core/src/test/kotlin/ru/lionzxy/tplauncher/utils/HttpDownloaderTest.kt core/build.gradle.kts
git commit -m "feat(downloader): gzip + conditional changelog GET (304) with on-disk cache

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 9: Full core build + regression gate

**Files:** none (verification task).

- [ ] **Step 1: Run the whole core + desktop test suites**

Run: `./gradlew :core:cleanTest :core:test :desktop:cleanTest :desktop:test`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 2: If anything fails, fix it before proceeding** (do not commit a red tree). When green, no commit needed (no file changes) — proceed to the server task.

---

### Task 10: Server — emit optional `sha256` map for changed files

**Files:**
- Modify: `~/private/GamesGlitchlessStatic/scripts/update-maker.py` (separate repo; create a branch `feature/changelog-sha256` there)
- Test: `~/private/GamesGlitchlessStatic/scripts/test_update_maker.py` (create) — a self-contained round-trip on a temp dir.

**Interfaces:**
- Produces: each `<pack>_changelog.json` gains a top-level `"sha256": {"/relpath": "<sha256hex>"}` carrying hashes for files added/changed in the run, accumulated across runs, with REMOVE paths pruned. Buckets and the MD5 index are unchanged.

- [ ] **Step 1: Branch the server repo**

```bash
cd ~/private/GamesGlitchlessStatic && git checkout -b feature/changelog-sha256 master
```

- [ ] **Step 2: Write the failing test**

Create `~/private/GamesGlitchlessStatic/scripts/test_update_maker.py`:
```python
import json, os, tempfile, importlib.util, hashlib

spec = importlib.util.spec_from_file_location(
    "um", os.path.join(os.path.dirname(__file__), "update-maker.py"))
um = importlib.util.module_from_spec(spec); spec.loader.exec_module(um)

def test_changelog_has_sha256_for_added_files_and_buckets_stay_int():
    with tempfile.TemporaryDirectory() as d:
        src = os.path.join(d, "incremental", "p"); os.makedirs(src)
        with open(os.path.join(src, "a.txt"), "w") as f: f.write("hello")
        idx = os.path.join(d, "incremental", "indexes", "p.json")
        os.makedirs(os.path.dirname(idx))
        clog = os.path.join(d, "incremental", "p_changelog.json")
        cwd = os.getcwd(); os.chdir(d)
        try:
            um.make_changelog("incremental/p", idx, clog)
        finally:
            os.chdir(cwd)
        data = json.load(open(clog))
        # buckets are still {path: 0|1} ints
        ts_keys = [k for k in data if k.isdigit()]
        assert ts_keys, data
        assert data[ts_keys[0]]["/a.txt"] == 1
        # optional sha256 map carries the file's SHA-256
        assert "sha256" in data
        assert data["sha256"]["/a.txt"] == hashlib.sha256(b"hello").hexdigest()
        # md5 index unchanged in form (hex md5)
        index = json.load(open(idx))
        assert index["incremental/p/a.txt"] == hashlib.md5(b"hello").hexdigest()

if __name__ == "__main__":
    test_changelog_has_sha256_for_added_files_and_buckets_stay_int()
    print("OK")
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd ~/private/GamesGlitchlessStatic && python3 scripts/test_update_maker.py`
Expected: FAIL — `KeyError: 'sha256'` (no sha256 map emitted yet).

- [ ] **Step 4: Implement the sha256 map**

In `scripts/update-maker.py`:

Add a SHA-256 helper near `checksum_file` (keep `checksum_file` as MD5 for the index/diff):
```python
def sha256_file(path):
    with open(path, mode='rb') as f:
        return hashlib.sha256(f.read()).hexdigest()
```

Replace `make_changelog` with a version that maintains the optional `"sha256"` map (note the changelog is held in memory with ABSOLUTE paths because `load_changelog` prefixes them; `save_changelog` strips the prefix back to `/relpath`, including for the `sha256` map):
```python
def make_changelog(source_dir_path, output_index_path, output_changelog_path):
    old_index = load_index(output_index_path)
    new_index = make_index(source_dir_path)
    diff = diff_index(old_index, new_index)
    changelog = load_changelog(source_dir_path, output_changelog_path)

    # Optional SHA-256 map (absolute paths in memory, like the buckets). Carry the prior map,
    # then set the hash for each ADD in this run and drop each REMOVE. Buckets stay {path: 0|1}.
    sha_map = changelog.pop('sha256', {})
    for path, action in diff.items():
        if action == 1:
            sha_map[path] = sha256_file(path)
        elif action == 0:
            sha_map.pop(path, None)

    add_diff_to_changelog(changelog, diff)
    changelog['sha256'] = sha_map

    print_diff(diff)

    if len(diff) != 0:
        save_index(new_index, output_index_path)
        save_changelog(source_dir_path, changelog, output_changelog_path)
```
Why this stays backward compatible: `load_changelog`/`save_changelog` already iterate every top-level key and add/remove the base-path prefix on the VALUES (a `{path: x}` dict), so the `sha256` map is prefix-handled exactly like a bucket; old launchers parse the changelog as `{ts: {path: 0|1}}` and the `"sha256"` key (non-numeric, string values) is ignored by their `toLongOrNull` bucket filter. `diff_index` still uses the MD5 index, so only genuinely-changed files are bucketed (no migration).

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd ~/private/GamesGlitchlessStatic && python3 scripts/test_update_maker.py`
Expected: prints `OK`.

- [ ] **Step 6: Sanity-check a second run prunes REMOVE + accumulates**

Run (manual): re-run `make_changelog` after deleting `a.txt` and adding `b.txt` in a scratch dir; confirm `sha256` drops `/a.txt` and adds `/b.txt`. (Optional extra assertion in the test if time permits.)

- [ ] **Step 7: Commit (server repo)**

```bash
cd ~/private/GamesGlitchlessStatic
git add scripts/update-maker.py scripts/test_update_maker.py
git commit -m "feat(changelog): emit optional sha256 map for changed files (backward compatible)

Keeps MD5 index for diffing (no migration) and {ts:{path:0|1}} buckets
(old clients unaffected); adds an optional top-level sha256 map carrying
SHA-256 for added/changed files, accumulated across runs, REMOVEs pruned.

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

### Task 11: Document the nginx gzip ops step

**Files:**
- Modify: `~/private/GamesGlitchlessStatic/README.md` (create if absent) OR add `scripts/README-ops.md`.

- [ ] **Step 1: Write the ops note**

Add a short section documenting the host-side enablement (not applied by code — the nginx vhost lives on the server):
```markdown
## Serving: gzip for JSON changelogs

Enable gzip for the JSON changelog/index responses in the nginx vhost (the mod
`.jar/.zip` files are already compressed — do not gzip them):

    gzip on;
    gzip_types application/json;
    gzip_min_length 1024;

Conditional GET (`ETag`/`If-None-Match` → `304`) is already supported by nginx's
default static handling; the launcher sends `If-None-Match` and caches the body.
```

- [ ] **Step 2: Commit (server repo)**

```bash
cd ~/private/GamesGlitchlessStatic
git add README.md  # or scripts/README-ops.md
git commit -m "docs(ops): enable nginx gzip for JSON changelogs

Claude-Session: https://claude.ai/code/session_01LDRxWjZ9HWS6m1GkepG55D"
```

---

## Final verification

- [ ] Client: `./gradlew :core:cleanTest :core:test :desktop:cleanTest :desktop:test` → all green.
- [ ] Client: `./gradlew :desktop:run` (manual smoke if feasible) → settings window shows the "Параллельных загрузок" field; editing + Применить persists.
- [ ] Server: `python3 scripts/test_update_maker.py` → `OK`.
- [ ] Confirm old-client compatibility by feeding a new-format changelog (with `"sha256"`) to the **old** `parseChangeLog` shape mentally / via a quick check: numeric buckets parse, `"sha256"` ignored.
