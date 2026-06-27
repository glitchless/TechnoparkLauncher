# Per-modpack managed JREs from `jres2.json`

**Date:** 2026-06-27
**Status:** Implemented

## Problem

The Java executable used to launch Minecraft is currently sourced from
`Settings.javaLocation`, whose lazy default (`SettingsDefault.getDefaultJavaLocation()`)
reads a single hardcoded `jrepath.txt` file written by an external installer. This means:

- There is exactly **one** JRE for all modpacks, regardless of the Minecraft version a
  modpack needs.
- The launcher cannot provision a JRE itself; it depends on an out-of-band installer
  having written `jrepath.txt` and populated the `jre/` directory.

We want each modpack to declare which JRE it needs (by a *code* such as `jre8` / `jre21`),
and have the launcher fetch the matching JRE for the current OS/architecture from the
server manifest at `https://minecraft.glitchless.ru/jres2.json`, verify it, install it,
and launch with it.

## Decisions (locked)

1. **No manual override.** The per-modpack managed JRE is the only source of the Java path.
   The manual "Путь до Java" field in Settings and `Settings.javaLocation` are removed
   entirely.
2. **Hard-fail when unavailable.** An already-installed matching JRE is always reused
   (including offline). But if the required JRE is **not** installed **and** it cannot be
   fetched/downloaded (offline, server down, missing entry), the launch fails with a clear,
   actionable error rather than silently falling back to a wrong system Java.
3. **Offline manifest cache.** The latest successfully-fetched `jres2.json` is cached to
   disk so the launcher can resolve/verify an already-installed JRE without internet.
4. **Delete the `jrepath.txt` mechanism completely.**

## Manifest format (`jres2.json`)

Top-level is a JSON **array** of entries. Each entry has a `code` and a list of per-platform
`files`:

```json
[
  {
    "code": "jre21",
    "files": [
      {
        "type": "Linux",
        "arch": "x86_64",
        "extension": "tar.gz",
        "downloadUrl": "https://minecraft.glitchless.ru/jres/jre-21-linux-x64.tar.gz",
        "javaRelativePath": "jre-21.0.11/bin/java",
        "SHA-256": "bSQf1wzJSvZnozyE7F+H0CthgVsIHSVcqPhaG233LUw=",
        "javaSHA-256": "RWIKRot34W0afUbhtHJEN/0lC6XYsuxCJBOZ4x01uDs="
      }
    ]
  }
]
```

- `type` ∈ `{Linux, Windows, macOS}` — **exactly** matches oslib
  `OperatingSystem.getOperatingSystem().type.getName()`.
- `arch` ∈ `{x86_64, arm64}` — matched against oslib `Arch.getSearch()`
  (`x86_64` → `Arch.x86_64`; `arm64` → `Arch.ARM`).
- `extension` ∈ `{tar.gz, zip}` (tar.gz for Linux/macOS, zip for Windows).
- `SHA-256` — base64-encoded SHA-256 of the **downloaded archive**.
- `javaSHA-256` — base64-encoded SHA-256 of the **extracted `java` binary** at
  `javaRelativePath` (relative to the install dir; includes the archive's top-level folder).

Both hashes are in the same base64 format produced by the existing
`File.generateSHA256()` (`config/SyncInfo.kt`).

## Architecture

### A. Manifest data model + selection (pure, no I/O)

New file (e.g. `core/.../minecraft/jre/JreManifest.kt`):

```kotlin
data class JreManifestEntry(val code: String, val files: List<JreFile>)

data class JreFile(
    val type: String,
    val arch: String,
    val extension: String,
    val downloadUrl: String,
    val javaRelativePath: String,
    @SerializedName("SHA-256") val sha256: String,
    @SerializedName("javaSHA-256") val javaSha256: String,
)
```

- Parse with `Gson().fromJson(json, Array<JreManifestEntry>::class.java).toList()`.
- Pure selection helpers (unit-testable, mirroring the `IncrementalDownloadLogic` split):
  - `List<JreManifestEntry>.findByCode(code): JreManifestEntry?`
  - `JreManifestEntry.selectFileFor(osName: String, arch: Arch): JreFile?` — picks the file
    where `file.type == osName && arch.search.any { it.equals(file.arch, ignoreCase = true) }`.

### B. `JreManager` (I/O orchestration)

New file (e.g. `core/.../minecraft/jre/JreManager.kt`). Singleton/object.

Responsibilities:

- **`fetchManifest(): List<JreManifestEntry>`**
  - `GET` `JRES_JSON_LINK` via `HttpDownloader.instance.getString(...)`.
  - On success: parse, then write the raw JSON to the disk cache
    `ConfigHelper.getJreManifestCacheFile()` (= `technomine/jre/jres2.json`).
  - On network failure: read and parse the cached file; log + Sentry the network error.
  - If neither network nor cache yields a manifest → throw (callers translate to hard-fail).

- **`ensureInstalled(javaCode: String, monitor: IProgressMonitor): File`**
  1. `entry = fetchManifest().findByCode(javaCode)` — null → throw with clear message.
  2. `file = entry.selectFileFor(osName, arch)` — null → throw (unsupported OS/arch).
  3. `installDir = ConfigHelper.getJreInstallDirectory(javaCode)` (= `technomine/jre/<code>/`);
     `javaBinary = resolveBinary(installDir, file)` (see below).
  4. **Already installed?** If `javaBinary.exists()` and
     `javaBinary.generateSHA256() == file.javaSha256` → cache & return it (no download;
     this is the offline / fast path).
  5. **Install:** download `file.downloadUrl` to a temp file (named with `file.extension` so
     the archiver can auto-detect) → verify archive `generateSHA256() == file.sha256` →
     delete `installDir` recursively → extract into `installDir` → verify
     `javaBinary.generateSHA256() == file.javaSha256` → on non-Windows
     `javaBinary.setExecutable(true)` → cache & return.
  6. Any failure where step 4 already succeeded returns the installed binary; otherwise the
     exception propagates (hard-fail).

- **`resolveJavaBinary(javaCode: String): File?`** — used at launch time after prepare.
  Returns the in-memory cached path if present; else resolves from the **cached** manifest
  + install dir (does **not** hit the network). Returns null only if nothing is installed
  and the manifest is unavailable.

- **`resolveBinary(installDir, file)`**: `File(installDir, file.javaRelativePath)`, except on
  Windows return the sibling `javaw.exe` (replicating the old `getDefaultJavaLocation()`
  behavior: GUI launch with no console window).

Extraction:
- Use `jarchivelib` (already a declared dependency, currently unused):
  `ArchiverFactory.createArchiver(tempArchiveFile).extract(tempArchiveFile, installDir)`.
  Auto-detects `.tar.gz` / `.zip` from the filename. commons-compress preserves the Unix
  executable bit from tar entries; the explicit `setExecutable(true)` on POSIX is a
  belt-and-suspenders. (Exact API to be confirmed against `jarchivelib:1.2.0` during
  implementation; `zt-zip`'s `ZipUtil.unpack` remains available as a zip fallback.)

Progress: set `monitor.setStatus("Загрузка Java...")` + stream progress via the existing
`TextProgressMonitor` pattern (as `InitialDownloader` does), and
`setStatus("Распаковка Java...")` + `setProgress(-1)` during extraction.

### C. Constants

In `MinecraftContext.kt` (next to `BASE_URL`):

```kotlin
const val JRES_JSON_LINK = "$BASE_URL/jres2.json"
```

### D. `MinecraftModpack` enum

Add a JRE-code field with a default so existing entries are unchanged:

```kotlin
enum class MinecraftModpack(
    val modpackName: String,
    val initialDownloadLink: String?,
    val updateJsonLink: String?,
    val updateHostLink: String?,
    val defaultServer: ServerInfo?,
    val version: String,
    val javaCode: String = "jre8",
) { ... }
```

All current entries (VANILLA 1.16.5, GTNH 1.7.10, NOMI 1.12.2) inherit `jre8`.

### E. Pipeline integration

`JreDownloader : IDownloader` (new, in `prepare/downloader/`):
- `shouldDownload(...) = true`
- `init(...)` = no-op
- `download(minecraft)` = `JreManager.ensureInstalled(minecraft.modpack.javaCode, minecraft.progressMonitor)`

Registered **first** in `ComposerDownloader.downloaders`, so the JRE is provisioned before
modpack content and a JRE failure aborts the launch early. Exceptions propagate through
`ComposerDownloader` → `ComposePrepare` → caught by `LauncherViewModel.onGameStart`
(→ `LaunchError`) and `MainCli` (→ printed + exit 1).

### F. Launch integration

`MinecraftLauncher.launch()`:
- `val javaFile = JreManager.resolveJavaBinary(minecraft.modpack.javaCode)
     ?: throw IllegalStateException("JRE '${minecraft.modpack.javaCode}' is not installed")`
- Construct `LauncherSettings(ConfigHelper.config.settings, additionalJavaArguments, javaFile)`.

`LauncherSettings`:
- New constructor param `private val javaLocation: File?`.
- `getJavaLocation()` no longer reads `settings.javaLocation`; it applies the existing
  Windows-non-ASCII 8.3 short-path logic to the injected `javaLocation` and returns it
  (null → mclauncher default, though in practice it is always non-null post-prepare).

### G. Removals (jrepath.txt + manual field)

- `ConfigHelper`: delete `getJREPathFile()` and `writeJREConfig()`. Add
  `getJreInstallDirectory(code)` and `getJreManifestCacheFile()`. Keep `getJavaDirectory()`
  (= `technomine/jre`, now the root for managed JREs + manifest cache).
- `SettingsDefault`: delete `getDefaultJavaLocation()`.
- `Settings`: remove `javaLocation` property, `javaLocationField`, and the copy-ctor line.
  Old configs with a `"javaLocation"` JSON key still deserialize (Gson ignores unknown keys).
- `SettingsViewModel`: remove `javaPath`, `onJavaPathChange`, and the
  `settings.javaLocation = ...` line in `apply()`. In `wipe()`, drop the `getJREPathFile()`
  exclusion; keep excluding `getJavaDirectory()` (preserves managed JREs + manifest cache).
- `SettingsWindow.kt`: remove the "Путь до Java" `TpField` row.
- `Strings`: remove `javaPath`.

### H. Install / cache layout

```
technomine/
  jre/
    jres2.json          <- cached manifest (offline resolution)
    jre8/               <- extracted JRE for code "jre8"
      jdk8u412-full.jdk/bin/java   (macOS arm64 example)
    jre21/              <- extracted JRE for code "jre21" (when a pack needs it)
```

Different OS/arch never coexist on one machine, so keying installs by `code` alone is safe.
`wipe()` preserves the whole `jre/` tree (including the manifest cache).

## Error handling & messages (Russian, matching existing UI)

- Download/extract progress: `"Загрузка Java..."`, `"Распаковка Java..."`.
- Manifest fetch failed but a JRE is installed → proceed silently (best-effort, log only).
- Hard-fail (not installed and cannot provision), surfaced via the existing `LaunchError`
  generic path; the thrown exception message is specific, e.g.
  `"Не удалось установить Java '<code>' для <os>/<arch>: <cause>"` for logs/Sentry.

## Testing

Unit tests (JUnit, hermetic — no real disk/network):

- **Selection logic:** for each `(type, arch)` combination in the sample manifest, the right
  `JreFile` is selected; unknown OS/arch → null; `arm64`↔`Arch.ARM` and `x86_64`↔`Arch.x86_64`
  mapping verified.
- **Manifest parse:** Gson round-trip of the real `jres2.json` shape, including the
  `SHA-256` / `javaSHA-256` key mapping.
- **Install decision:** binary present + hash match → no download; hash mismatch / missing →
  install path taken (verify via injected seams).
- **`JreManager` offline behavior:** with Ktor `MockEngine` — online fetch writes the cache;
  offline read uses the cache; nothing installed + no manifest → throws (hard-fail).
- **Updated existing tests:** `SettingsGsonTest` (drop `javaLocation` assertions),
  `SettingsViewModelTest` (no `javaPath`), `SettingsSnapshotTest` (no `settings.javaLocation`).

To keep `JreManager` testable, factor the byte-level work (hashing, selection, path
resolution, install-needed decision) into pure functions; isolate HTTP + extraction behind
seams that tests can substitute, following the `IncrementalDownloader` /
`IncrementalDownloadLogic` precedent.

## Out of scope

- No UI for choosing a JRE (the code is fixed per modpack in the enum).
- No concurrent multi-JRE prefetch; only the current modpack's JRE is provisioned per launch.
- No change to heap/args/prefix settings.
