# TechnoparkLauncher: JavaFX/TornadoFX → Jetpack Compose for Desktop

**Date:** 2026-06-26
**Branch:** `feature/compose`
**Status:** Design approved — pending implementation plan

---

## 1. Context & goals

TechnoparkLauncher (a.k.a. Glitchless Games Launcher) is a Kotlin desktop
Minecraft launcher built on **TornadoFX 1.7.20 / JavaFX**, pinned to **Kotlin
1.3.61, JDK 8, Gradle 5.6.4**. It authenticates against a custom Yggdrasil
server, downloads/syncs modpacks, and spawns Minecraft as a separate process on
a bundled JRE 8.

The goal is to **rewrite the UI in Jetpack Compose for Desktop (JVM)** while
preserving all current behavior. A six-pass codebase mapping established the key
fact that shapes everything: **this is a UI rewrite, not a logic rewrite.** Of
74 Kotlin files, **53 are framework-free and port unchanged**; only the `view/`
package, the app entry points, and a handful of UI-typed utilities are
JavaFX-coupled, plus **exactly one line** of business logic.

### Non-goals (this migration)
- No behavioral changes — see §9 for the bugs we deliberately preserve.
- No redesign — faithful pixel-clone of the current look.
- No changes to the game-launch engine, auth, downloaders, or sync logic.
- No change to the self-update wire format or the server side.

---

## 2. Decisions (locked)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Repo/toolchain strategy | **Multi-module:** extract `:core` (reused logic), new `:desktop` Compose UI; CLI keeps using `:core`. Big-bang toolchain bump (Kotlin 2.x / JDK 17 / Gradle 8). |
| 2 | Visual fidelity | **Faithful pixel-clone** of the current UI. |
| 3 | Theming | **Bespoke composables** — custom theme, no Material. |
| 4 | Distribution & self-update | **Cross-platform fat jar** (all Skiko natives bundled) + bundled launcher JRE 17; keep `upload.sh` / `launcher.json` self-update unchanged. |
| 5 | Known bugs | **Faithful port now**, fix in follow-up PRs. |
| — | Localization (default) | Keep hardcoded **Russian** literals, centralized into one `Strings` object in `:desktop`; drop the out-of-sync `strings_en_US.properties`. |

---

## 3. Target architecture

```
settings.gradle.kts            Kotlin 2.4.0 · JDK 17 · Gradle 9.6.0 · Compose MP 1.11.1
│
├── :core    (id "org.jetbrains.kotlin.jvm")  — pure JVM library, no UI framework
│     • all framework-free business logic (auth, download, sync, launch chain,
│       config, OS workarounds) — see §4 disposition table
│     • the headless CLI entry point (regression harness)
│     • depends on: mclauncher-api, oslib, JNA, Gson, sentry, commons-codec,
│       jarchivelib, zt-zip, kotlinx-coroutines-core
│       (sentry: :core uses Sentry.capture/getContext in MinecraftAccountManager
│        + Extensions; the SentryClient init/DSN moves to the :desktop app shell.
│        json-smart is used only by Avatar.kt → :desktop, and is dropped — §13)
│
└── :desktop (id "org.jetbrains.compose" + "org.jetbrains.kotlin.plugin.compose")
      • Compose application: Main → application { Window { … } }
      • theme, components, screens, ViewModel, window chrome, vector icons
      • depends on: project(":core"), compose.desktop.currentOs (+ all Skiko
        platform natives for the cross-platform uber jar)
```

### Dependency changes (all deps → latest stable)
Per the user's request, **every dependency is upgraded to its latest stable
version** (verified on Maven Central / JitPack on 2026-06-26). The full pinned
table, the mutually-compatible version triple, and the breaking-change
migrations live in **§13**. Headlines:
- **Remove (JavaFX stack):** `no.tornado:tornadofx`,
  `de.codecentric.centerdevice:javafxsvg`, `kotlinx-coroutines-javafx`, the
  `javafx-gradle-plugin` + its `jfx{}` block, and the abandoned `de.fuerstenau`
  BuildConfig plugin. Also drop the redundant `kotlin-stdlib-jdk8` and the dead
  `jcenter`/`bintray` repos.
- **Add:** `org.jetbrains.compose` 1.11.1 + the bundled Compose-compiler plugin
  `org.jetbrains.kotlin.plugin.compose` (version = Kotlin version),
  `compose.desktop`, and the four `skiko-awt-runtime-*` natives (linux-x64,
  macos-x64, macos-arm64, windows-x64) for the cross-platform uber jar. Replace
  BuildConfig with `com.github.gmazzo.buildconfig` 6.0.10 (keeps
  `BuildConfig.NAME`/`.VERSION` call sites unchanged).
- **Major code-impact bumps:** the Kotlin/Gradle toolchain (K2) and `io.sentry`
  1.7.5 → 8.46.0 (full API rewrite — §13.3). `json-smart` (single site,
  `Avatar.kt:83`) is **dropped** in favor of Gson during the Avatar rewrite.
- **Zero-impact bumps:** Gson 2.14.0, JNA 5.19.1, commons-codec 1.22.0,
  jarchivelib 1.2.0, zt-zip 1.17, coroutines-core 1.11.0, JUnit 4.13.2; bump
  `oslib` to `4a529cbef2`; **keep** `mclauncher-api` at `37e0f29fc7` (already
  master HEAD). The game's JRE-8 download scheme is untouched.

### Build tasks
- `fatJar` → Compose uber jar (`packageUberJarForCurrentOS`) **extended to
  bundle all Skiko native variants** so it stays one cross-platform artifact
  that `upload.sh` can hash and publish.
- `runCli` → hand-written `JavaExec` against `:core`'s CLI main (preserved
  verbatim, incl. the `TPL_EMAIL`/`TPL_PASSWORD` env-var plumbing).
- `run` → Compose `run` task. **Drop** the `GDK_BACKEND=x11` Wayland workaround
  — it is JavaFX/GTK-specific and irrelevant under Skiko/Skia.

---

## 4. File disposition

Legend: **Move** = port verbatim to `:core`. **Rewrite** = re-author in
`:desktop`. **Split** = agnostic parts to `:core`, UI parts rewritten in
`:desktop`. **Adapt** = move to `:core` with a targeted change. **Drop** = delete.

| File / package | Disposition | Notes |
|---|---|---|
| `data/`, `config/`, `exceptions/` | **Move** → :core | Zero UI imports (incl. `Settings.java`, `SettingsDefault.kt`, `Config.kt`, etc.). |
| `minecraft/` (+ `workarounds/`, `delegates/`) | **Move** → :core | `MinecraftContext`, `MinecraftAccountManager`, `MinecraftLauncher`, `LauncherSettings.getJavaLocation()`, all OS workarounds. |
| `prepare/` (+ `downloader/`, `sync/`, `processing/`) | **Move** → :core | `ComposePrepare` (name is coincidental — keep it), all downloaders, `SyncManager`, `MergeOptionsProcessing`. |
| `prepare/downloader/base/IncrementalDownloader.kt` | **Adapt** → :core | Line 102 `withContext(Dispatchers.Main)` → drop the UI hop (use `Dispatchers.Default`); the progress monitor becomes thread-safe (§6). |
| `utils/ConfigHelper.kt`, `WindowsPathHelper.kt`, `SystemMemoryHelper.kt`, `UrlDownloader.kt`, `HttpUserAgent.kt`, `TextProgressMonitor.kt`, `EmptyMonitoring.kt`, `DebugMonitoring.kt`, `UriEncodeUtils.java` | **Move** → :core | Framework-free helpers. |
| `utils/Extensions.kt` | **Split** | File/String/byte/collection helpers → :core; any `Node`/`ImageView`/`Platform.runLater`/`svgview` helpers → :desktop. |
| `utils/LogoUtils.kt` | **Split** | `setLogoForMinecraft(ctx)` (writes game logo file, called from launch chain) → :core; JavaFX `Stage` icon / SVG-install parts → :desktop. |
| `utils/Constants.kt` | **Rewrite** → :desktop | JavaFX `Color`/`tornadofx.c()` → Compose `Color` in `TpColors`; `DEFAULT_MARGIN` → theme spacing. |
| `utils/ResourceHelper.kt` | **Rewrite** → :desktop | JavaFX `Font.loadFont` → Compose `Font(...)` → `FontFamily`. |
| `utils/LocalizationHelper.kt` | **Drop / fold** | Superseded by the centralized `Strings` object (§ Decision 5 default). |
| `MainCli.kt` | **Move** → :core | Headless login→prepare→launch; the cleanest reference for the ViewModel and a regression harness. |
| `Main.kt`, `MainApplication.kt` | **Rewrite** → :desktop | TornadoFX `App` + `Application.launch` + `com.sun.javafx…PlatformImpl` → Compose `application {}`. |
| `view/main/MainController.kt` | **Rewrite** → :desktop | Becomes `LauncherViewModel` (§6). |
| `view/main/states/*` | **Rewrite** → :desktop | Collapse into the sealed `LauncherState` (§6). |
| `view/main/StateHelper.kt` | **Drop** | Empty class — dead code. |
| `view/main/ProgressDelegate.kt` | **Rewrite** → :desktop | Replaced by a `StateFlow`-backed progress monitor (§6). |
| `view/main/MainWindow.kt`, `view/main/listener/*` | **Rewrite** → :desktop | Window + chrome + drag/close/site/settings (§7). |
| `view/settings/SettingsWindow.kt`, `view/settings/listener/*` | **Rewrite** → :desktop | Second window + actions (§7). |
| `view/common/Avatar.kt`, `MyCheckBox.kt`, `GlobalStylesheet.kt` | **Rewrite** → :desktop | Bespoke composables + theme (§5). |
| `src/main/resources/` fonts/icons/strings | **Split** | Fonts + icons (→ vectors) move to `:desktop` resources; `jres.json` stays with `:core`. |

---

## 5. Theme & component library (bespoke)

No `MaterialTheme`. A small hand-built theme exposed via `CompositionLocal`:

- **`TpColors`** — the 11 tokens from `Constants.kt`, verbatim hex:
  `accent #00DB9D` · `text #FFFFFF` · `background #36393E` ·
  `backgroundDark #2F3136` · `backgroundCircle #2B2C31` ·
  `inputBackground #484C51` · `backgroundProgressBar #DDDDDE` ·
  `textDisable #AAABAD` · `disable #7F8185` · `error #D75379`.
- **`TpTypography`** — `Gugi-Regular` 30sp (title, accent) · `Roboto-Regular`
  14sp (body) / 16sp (button) / 12sp (caption, `#AAABAD`). Fonts bundled as
  Compose resources.
- **Spacing/shape** — base unit `16.dp` (`DEFAULT_MARGIN`); 23dp left gutter;
  radii button 3.5dp / progress 5dp / checkbox 2.5dp; window 592dp wide;
  checkbox 28dp (16dp check) / avatar 84dp / gear 34dp / close-✕ 20dp / login
  button minHeight 36dp.
- **Components** (1:1 with current): `TpButton`, `TpTextField`, `TpServerCombo`,
  `TpCheckBox`, `Avatar` (async load, circle-clip), `TpProgressBar`
  (determinate + indeterminate), bottom progress panel, close-✕, gear, register
  link, title.
- **Icons** — `times`, `cogs`, `check`, and the combobox chevron converted from
  SVG to `ImageVector`; fills (currently baked into the SVGs) re-tinted at
  runtime.
- **Disabled = explicit color, not alpha** — faithfully reproduce the current
  `disabled { opacity: 1.0 }` behavior; never rely on Compose's default alpha
  dimming.

---

## 6. State model, ViewModel & threading

### State model
The 16-flag `BaseState` + 9 subclasses collapse into one immutable sealed model
the UI renders declaratively:

```kotlin
sealed interface LauncherState
data object Initial                              : LauncherState
data class  InitialError(val msg: String)        : LauncherState
data object LoginProgress                        : LauncherState
data class  Logged(val email: String)            : LauncherState
data class  GameLoading(val email: String)       : LauncherState
data class  MinecraftRunning(val email: String)  : LauncherState
data class  MinecraftLaunched(val email: String) : LauncherState  // terminal → exit
data class  LaunchError(val email: String, val msg: String) : LauncherState
```

Each variant maps to exactly the same visual flags as today, including quirks
(error text inside the disabled button label; the `UnknownHostException` →
initial-error route). Progress is a separate stream, not part of `LauncherState`.

### ViewModel
`LauncherViewModel` replaces `MainController` with the **same method surface**:
`onInitView()`, `onButtonClick(email, password)`, `onLogin(...)`,
`onGameStart(...)`, `onChangeModpack(pack)`, `onPasswordOrLoginChange()`.

- Exposes `StateFlow<LauncherState>`.
- Runs the login→prepare→launch chain on a background dispatcher (mirrors the
  current `runAsync` on a cached thread pool); emits state on the UI dispatcher.
- Holds the same `MinecraftContext` construction from `ConfigHelper.config`.
- `MainCli` remains the parallel headless caller of the same `:core` entry
  points.

### Progress
A `:core` `IProgressMonitor` implementation backed by
`MutableStateFlow<ProgressUiState>` (status text + value, `-1` = indeterminate)
replaces `ProgressDelegate`. The composable collects it. Because the monitor is
now thread-safe, **`IncrementalDownloader.kt:102` drops its
`withContext(Dispatchers.Main)` hop** — eliminating the only hard JavaFX runtime
coupling without pulling in `coroutines-javafx` or `-swing`. (Fallback if a UI
hop is ever needed: Compose Desktop's `Dispatchers.Main` is supplied by
`kotlinx-coroutines-swing`, the AWT/Swing EDT.) On the coroutines 1.11.0 bump,
`newFixedThreadPoolContext` (`IncrementalDownloader.kt:28`) now requires
`@OptIn(DelicateCoroutinesApi::class)`.

`sleep(60s)` in the launch flow is preserved verbatim (deferred bug, §9).

---

## 7. Window composition

- **Entry:** `application { … }`. `configureHttpUserAgent()` runs **first**
  (before any window — preserves the Cloudflare `Java/` UA 403 fix); Sentry
  initialized in the application scope. Replaces `Application.launch` +
  TornadoFX `App` + the JDK-8-only `com.sun.javafx…PlatformImpl` finish listener.
- **Main window:** `Window(undecorated = true, resizable = false)`, width 592dp,
  height wraps content (replaces `sizeToScene()` after each state change).
  Drag-anywhere chrome via `WindowDraggableArea`, scoped to background regions so
  it does not swallow clicks on fields, the combobox, or buttons.
- **Settings window:** a second `Window` emitted conditionally from
  `showSettings: MutableState<Boolean>` inside `application {}` — state-driven,
  replacing the imperative lazy-singleton `View.openWindow(UNDECORATED)`.
- **Taskbar icon:** set via `Window(icon = …)` from the bundled logo.

---

## 8. Distribution & self-update

**Finding:** `launcher.json` is referenced by **no Kotlin code in this repo**.
Self-update is handled by an **external bootstrapper** (a separate project) that
reads `launcher.json` (`{version, downloadFullPath, SHA-256}`), compares
versions/hash, downloads the new jar from `minecraft.glitchless.ru`, and runs
it. `scripts/upload.sh` only builds the fat jar, hashes it (via `Hasher.jar` /
oslib), regenerates `launcher.json`, and uploads.

**Plan:**
- `:desktop` produces a **cross-platform uber jar** with all Skiko native
  variants bundled (Skiko extracts the matching native at runtime). It stays a
  runnable jar, so `upload.sh` + `launcher.json` self-update **work unchanged**.
- **Coordination item (out of repo, not a code change here):** the external
  bootstrapper currently launches the jar with Java 8; post-migration it must
  provide/bundle a **JRE 17** for the launcher jar. (Optionally, a later jlink/
  `jpackage` step can bundle JRE 17 alongside the jar.)
- The **game's** JRE-8 mechanism (`jres.json` → download/extract →
  `jrepath.txt` → `LauncherSettings.getJavaLocation()`) is preserved verbatim in
  `:core`. Two bundled runtimes total: **17 for the launcher, 8 for the game.**

---

## 9. Faithful-clone behavior & deferred bugs

Preserved exactly (replicated, not "fixed"):
1. **Settings saves/closes even when heap validation fails** — the catch shows
   the error but does not gate the save/close.
2. **`UnknownHostException` during launch routes to the initial/login error
   state**, not the launch-error state.
3. **`sleep(60s)`** stands in for real process-exit monitoring before the
   terminal `MinecraftLaunched` state.
4. **Destructive settings actions** (wipe game & reset, clear backup) have **no
   confirmation**.

These are documented here and filed for **follow-up PRs** after the migration
lands, keeping the migration diff a pure framework swap.

---

## 10. Testing & verification

- **`:core` unit tests** — the four existing tests (`AuthErrorsTest`,
  `WindowsPathFixTest`, `HttpUserAgentTest`, `WindowsPathHelperTest`) all
  exercise framework-free logic. They move to `:core` and **must stay green** —
  this is the proof that the logic port is clean.
- **`:desktop` build** — the cross-platform uber jar builds and launches.
- **Visual parity** — run the app and screenshot all 9 main-window states +
  the settings window; compare against the current JavaFX look (the
  faithful-clone gate).
- **End-to-end** — `runCli` still drives login → prepare → launch headlessly as
  a regression harness, against the real custom Yggdrasil server.

---

## 11. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Three-axis toolchain bump (Kotlin/JDK/Gradle) must land before any UI compiles | High | Stand up `:core` on the new toolchain first (it has no UI deps) and get its tests green before touching `:desktop`. |
| `IncrementalDownloader.kt:102` `Dispatchers.Main` throws at runtime once JavaFX is gone | Critical | Fixed early (§6) — thread-safe monitor, drop the UI hop. |
| Drag-anywhere chrome swallowing widget clicks | Medium | Scope `WindowDraggableArea` to background regions only; verify each interactive control. |
| SVG → vector re-tint fidelity | Medium | Convert to `ImageVector`, tint at runtime; visually diff against current icons. |
| External bootstrapper still launches with Java 8 | Medium | Coordination item flagged (§8); JRE 17 delivery handled in the bootstrapper project. |
| Skiko native bundling for a cross-platform jar | Low–Medium | Explicitly add all four `skiko-awt-runtime-*` artifacts; test on each OS. |

---

## 12. Out of scope (follow-ups)
- Fixing the four deferred bugs (§9).
- Native installers via `jpackage` (kept as an option, not this migration).
- Any UI redesign or new features.
- A real i18n layer (Russian literals centralized, but not externalized).

---

## 13. Dependency upgrades (pinned — verified 2026-06-26)

All dependencies upgrade to their latest stable versions (user request),
verified on Maven Central (`maven-metadata.xml` timestamps) / JitPack on
2026-06-26. This is a **coordinated toolchain rewrite**, not independent bumps:
Gradle 9 requires JDK 17 to run, Compose Desktop targets JDK 11+, and the build
moves from the Groovy `buildscript{}`/`apply plugin` style to the `plugins{}`
DSL + version catalog, dropping the dead `jcenter`/`bintray` repos.

### 13.1 Upgrade table

| Coordinate | Current | Target | Impact | Notes |
|---|---|---|---|---|
| **Toolchain** | | | | |
| `org.jetbrains.kotlin.jvm` | 1.3.61 | **2.4.0** | major | K2 default since 2.0 — stricter nullability/smart-cast/overload resolution. `kotlinOptions{}` → `compilerOptions{}`. |
| `org.jetbrains.compose` | — | **1.11.1** | major | New. Replaces the JavaFX/TornadoFX UI. CMP 1.11.1 ↔ Jetpack Compose 1.11.2; requires Kotlin ≥ 2.1.0. |
| `org.jetbrains.kotlin.plugin.compose` | — | **= Kotlin (2.4.0)** | minor | Compose compiler ships inside Kotlin since 2.0 — **version always equals the Kotlin version**, never pinned independently. |
| Gradle wrapper | 5.6.4 | **9.6.0** | major | `compile`/`testCompile`/`runtime` → `implementation`/`testImplementation`; `Jar.baseName` → `archiveBaseName`; needs JDK 17 to run. |
| `kotlin-stdlib-jdk8` | (via plugin) | **REMOVE** | minor | Since Kotlin 1.8, `-jdk7/-jdk8` merged into `kotlin-stdlib` (added automatically). |
| **Libraries** | | | | |
| `com.google.code.gson:gson` | 2.8.5 | **2.14.0** | none | API used is stable. 2.14.0 rejects duplicate JSON keys — smoke-test config/asset parsing. |
| `kotlinx-coroutines-core` | 1.3.3 | **1.11.0** | minor | `newFixedThreadPoolContext` (`IncrementalDownloader.kt:28`) now `@DelicateCoroutinesApi` → add `@OptIn`. See §13.2 Kotlin tension. |
| `kotlinx-coroutines-javafx` | 1.3.3 | **REMOVED** | minor | JavaFX-bound; dropped (see §6 for the `Dispatchers.Main` resolution). |
| `net.java.dev.jna:jna` | 5.13.0 | **5.19.1** | none | `Native.load`/`StdCallLibrary`/`WString` (`WindowsPathHelper`) stable; better JDK-17 strong-encapsulation handling. |
| `commons-codec:commons-codec` | 1.12 | **1.22.0** | none | Only `DigestUtils.sha1Hex(InputStream)` used (`Extensions.kt`). |
| `io.sentry:sentry` | 1.7.5 | **8.46.0** | major | Entire 1.x static-client API removed. Full migration §13.3. Min Java still 8. |
| `net.minidev:json-smart` | 1.1.1 | **DROP** (or 2.6.0) | none | Single site `Avatar.kt:83`; re-parse the tiny payload with Gson in the Avatar rewrite and drop the dep (also clears CVEs + the `accessor-smart`/ASM transitives). |
| `org.rauschig:jarchivelib` | 1.0.0 | **1.2.0** | none | API-compatible; bundled `commons-compress` → 1.21. |
| `org.zeroturnaround:zt-zip` | 1.14 | **1.17** | none | Maintenance only; `ZipUtil` facade stable. |
| `junit:junit` | 4.12 | **4.13.2** | none | Stay on JUnit 4 (final maintenance release); near-zero test code today. JUnit 5 optional, not worth it now. |
| **Build plugins** | | | | |
| `de.fuerstenau:BuildConfigPlugin` | 1.1.8 | **→ `com.github.gmazzo.buildconfig` 6.0.10** | minor | Old plugin abandoned/breaks on Gradle 8+. gmazzo 6.x needs Gradle 8.3+ (satisfied). Keep `className('BuildConfig')` + `packageName(project.group)`. |
| `javafx-gradle-plugin` | 8.8.2 | **REMOVED** | major | Obsoleted by `compose.desktop` packaging. |
| **JitPack pins** | | | | |
| `com.github.LionZXY:mclauncher-api` | `37e0f29fc7` | **KEEP `37e0f29fc7`** | none | Already master HEAD (2023-03-12). Do NOT "upgrade" to tag `0.3.2` (2019, older). |
| `com.github.LionZXY:oslib` | `d5ba9facde` | **BUMP `4a529cbef2`** | none | HEAD; +arm64 arch detection + uname fix, purely additive. |
| **Removed UI stack** | | | | |
| `no.tornado:tornadofx`, `de.codecentric.centerdevice:javafxsvg` | (current) | **REMOVED** | major | Replaced by Compose. Confirm exact declared versions before deleting the lines. |

### 13.2 Compatibility triple (must be used together)

> **Kotlin `2.4.0` + `org.jetbrains.compose` `1.11.1` + `org.jetbrains.kotlin.plugin.compose` `2.4.0`, on Gradle `9.6.0`, targeting JDK `17`.**

Hard rules: (1) the Compose-compiler plugin version **always equals** the Kotlin
version (`version.ref = kotlin`); (2) JetBrains guarantees latest CMP ↔ latest
Kotlin, so 1.11.1 + 2.4.0 needs no manual alignment (CMP's higher Kotlin minimums
apply only to iOS/web, irrelevant here); (3) raise `sourceCompatibility`/
`jvmTarget`/toolchain to 17.

> **⚠ Tension to resolve at build time (medium confidence):** toolchain research
> says Kotlin 2.4.0; coroutines research says `coroutines-core:1.11.0` was built
> against Kotlin ~2.2.x. In practice this is a *"compiled with an incompatible
> version of Kotlin"* **warning, not an error**, and 2.4.0 + 1.11.0 is expected
> to work. **Mitigation:** watch the first compile; if it escalates to an error,
> either use a coroutines build targeting 2.4.x (if one exists by then) or fall
> back to Kotlin 2.2.x (still ≥ CMP's 2.1.0 floor, triple stays valid). Verify on
> Maven Central at implementation time — don't assume.

### 13.3 Sentry 1.7.5 → 8.46.0 (call-by-call)

The 1.x static-client API is gone. Single coordinate `io.sentry:sentry:8.46.0`.
Because `Extensions.kt` is split, **both `:core` and `:desktop` depend on
`io.sentry:sentry`**.

| Old call (file:line) | New call | Module |
|---|---|---|
| `SentryClientFactory.sentryClient(dsn).apply { serverName=…; tags[…]=… }` — `MainApplication.kt:22-27` | `Sentry.init { o -> o.dsn=…; o.serverName=BuildConfig.NAME; o.release=BuildConfig.VERSION; o.setTag("version", BuildConfig.VERSION) }` | :desktop |
| `Sentry.setStoredClient(SENTRY)` — `MainApplication.kt:32` | **Delete** (Sentry is fully static in 8.x); delete `val SENTRY` + `SentryClient`/`SentryClientFactory` imports | :desktop |
| `import io.sentry.event.EventBuilder` — `MainApplication.kt:8` | **Delete** — verified dead import | :desktop |
| `Sentry.capture(e)` — `Extensions.kt:36`, `MainController.kt:91` | `Sentry.captureException(e)` (ignore returned `SentryId`) | split / :desktop |
| `Sentry.getContext().setUser(profile)` — `MinecraftAccountManager.kt:23,49` | `Sentry.setUser(io.sentry.protocol.User().apply { id=…; username=…; email=… })`; clear via `Sentry.setUser(null)` | :core |
| `fun Context.setUser(profile)` building `io.sentry.event.User(...)` — `Extensions.kt:107-110` | **Rewrite** to call `Sentry.setUser(...)`; `io.sentry.context.Context` + `io.sentry.event.User` removed. **Also fixes a latent bug:** the current body builds a `User` and never assigns it (no-op today). | split (called from :core) |

### 13.4 json-smart

Used directly at exactly one site — `Avatar.kt:83-84`, parsing
`{"data":{"avatar_url":"…"}}` with `JSONParser(MODE_PERMISSIVE)`. `Avatar.kt` is
rewritten in `:desktop` anyway. **Decision: drop json-smart**, re-parse with the
already-present Gson. (If kept, 2.6.0 is a drop-in with no code change and clears
CVEs, but adds `accessor-smart` + ASM transitives.)

### 13.5 Verification ordering

Set `JAVA_HOME` to a JDK 17. Land the **zero-impact bumps first** (gson, jna,
commons-codec, jarchivelib, zt-zip, junit, oslib SHA) to confirm a green
baseline, *then* the major toolchain + Sentry + UI changes — so a regression is
attributable to the risky change, not a safe one.

1. **Build:** re-run the wrapper to 9.6.0, then `./gradlew clean build` — catches
   K2 strictness, removed Sentry 1.x symbols, the `@DelicateCoroutinesApi`
   opt-in, `compile`→`implementation` renames, and the BuildConfig swap (confirm
   `BuildConfig.NAME`/`.VERSION` still resolve).
2. **Tests:** `./gradlew test` (`:core`, JUnit 4.13.2 / `testImplementation`).
3. **Smoke behavior:** config load/save + incremental download (Gson 2.14.0
   duplicate-key behavior); avatar fetch (Gson-replacement path); Windows short
   paths (JNA 5.19.1); JRE archive extraction (jarchivelib/zt-zip).
4. **Run + Sentry:** launch the app on JDK 17; confirm `Sentry.init` runs once,
   force a captured exception (reaches the DSN with `version` tag + serverName),
   and confirm the user is attached after login (validates the `setUser`
   rewrite). Confirm `runCli` still drives the `:core` login→launch flow.
