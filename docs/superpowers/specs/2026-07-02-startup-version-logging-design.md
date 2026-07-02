# Log the launcher version on startup

**Date:** 2026-07-02
**Status:** Designed

## Problem

The launcher version is baked in at build time (`2.<MAJOR_VERSION>.<MINOR_VERSION>`,
where `MAJOR_VERSION` comes from `gradle.properties` and `MINOR_VERSION` from the CI
build-number env var) and is already exposed to code as `BuildConfig.VERSION` via
gmazzo's `com.github.gmazzo.buildconfig` plugin. Today it is only used to tag Sentry
events (`desktop/Main.kt`). It is **never written to the session log**, so a user's
support log gives no indication of which launcher build produced it. This matters
because most of this launcher's troubleshooting is build/OS/JVM specific (Windows
paths, Dr.Web socket blocks, per-modpack managed JREs).

We want the version — plus the JVM and OS/arch it is running on — logged as the first
line of every session log, for both launch paths: the GUI (`desktop/Main.kt`) and the
headless CLI harness (`core/MainCli.kt`).

## Decisions (locked)

1. **Reuse the existing gmazzo `buildconfig` plugin.** Do **not** migrate to
   yshrsmz/BuildKonfig (`com.codingfeline.buildkonfig`). BuildKonfig is a different,
   Kotlin-Multiplatform-oriented plugin; gmazzo is the better fit for this `kotlin.jvm`
   multi-module project and is already integrated and working.
2. **`:core` becomes the single source of build metadata.** Move the `buildConfig { }`
   block (fields `NAME`, `VERSION`, `SENTRY_DSN`) from `:desktop` into `:core`.
   `:desktop` drops the `buildconfig` plugin and block and consumes `:core`'s generated
   class (it already depends on `:core`).
3. **Generate `BuildConfig` as `public`.** gmazzo defaults to `internal object
   BuildConfig`, which is only visible within its own Gradle module. Because `:desktop`
   is a separate module, `:core`'s `BuildConfig` must be public
   (`useKotlinOutput { internalVisibility = false }`) for `desktop/Main.kt` to keep
   using it.
4. **Keep the class coordinates identical:** package `ru.lionzxy.tplauncher`, class
   `BuildConfig`, same three fields. This means `desktop/Main.kt`'s existing Sentry code
   (`BuildConfig.SENTRY_DSN` / `NAME` / `VERSION`) compiles **unchanged** — it is just
   sourced from `:core` now — and the CLI in `:core` gains access for free.
5. **Enriched banner.** Log `name + version + jvm + os + arch`, not just the version.
   Cheap, and high-value for support given this project's OS/JVM-specific failure modes.

## Why the version must live in `:core`

The chosen scope is "GUI **and** headless CLI." The CLI's `main()` is
`ru.lionzxy.tplauncher.MainCliKt` in `:core` and never runs any `:desktop` code, so
threading the version down from `:desktop` at runtime cannot reach it. The version has
to be resolvable from within `:core` itself. Adding a *second* `buildConfig` generator
in `:core` while leaving `:desktop`'s in place would produce two
`ru.lionzxy.tplauncher.BuildConfig` classes on `:desktop`'s runtime classpath (a
duplicate-class clash), so the block is **moved**, not duplicated.

## Design

### 1. Gradle — `core/build.gradle.kts`

Add the plugin alias and the `buildConfig` block (moved verbatim from desktop, plus the
public-visibility directive):

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)   // added
}

// ...

buildConfig {
    useKotlinOutput { internalVisibility = false }   // public, so :desktop can consume it
    packageName("ru.lionzxy.tplauncher")
    buildConfigField("String", "NAME", "\"TechnoparkLauncher\"")
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "SENTRY_DSN", "\"https://cd312e191fbd44b49c6cc526bb91817c@sentry.team.glitchless.ru/18\"")
}
```

`project.version` in `:core` is set by the root `allprojects { version = "2.$major.$minor" }`,
so the value is identical to what `:desktop` produced.

### 2. Gradle — `desktop/build.gradle.kts`

Remove the `buildconfig` plugin alias and the entire `buildConfig { }` block. Nothing
else changes; `implementation(project(":core"))` puts `:core`'s public `BuildConfig` on
desktop's compile classpath.

### 3. New — `core/src/main/kotlin/ru/lionzxy/tplauncher/AppInfo.kt`

A single, testable helper so both entry points emit an identical banner:

```kotlin
package ru.lionzxy.tplauncher

import ru.lionzxy.tplauncher.log.Logger

/**
 * Static build/runtime facts about the launcher, sourced from the generated [BuildConfig].
 * Lives in :core so both the GUI (desktop Main) and the headless CLI can log an identical
 * startup banner as the first line of every session log.
 */
object AppInfo {
    val name: String get() = BuildConfig.NAME
    val version: String get() = BuildConfig.VERSION

    /** One-line summary for the top of a session log / support report. */
    val startupBanner: String
        get() = "$name v$version (jvm=${System.getProperty("java.version")}, " +
            "os=${System.getProperty("os.name")} ${System.getProperty("os.arch")})"

    /** Log [startupBanner] at INFO. Call once, first thing at process start. */
    fun logStartup() = Logger.i(LOG_TAG, startupBanner)
}

private const val LOG_TAG = "Launcher"
```

### 4. `desktop/src/main/kotlin/ru/lionzxy/tplauncher/Main.kt`

Add `AppInfo.logStartup()` **immediately after `Sentry.init`** (and before
`LogoUtils.prepareLogo()`). It is the first `Logger` call in the process, which lazily
creates the logs directory and opens the per-launch log file — so it is deliberately
placed *after* `Sentry.init` so that a failure to open the session log (an unwritable or
full logs dir) is still reported to Sentry. Nothing between the UA setup and this call
logs anything, so the banner is still the **first record** in the session log.

### 5. `core/src/main/kotlin/ru/lionzxy/tplauncher/MainCli.kt`

Add `AppInfo.logStartup()` at the top of `main()` (before `configureHttpUserAgent()`).
`Logger` mirrors INFO to stdout, so the CLI also prints the banner — no extra `println`.

## Testing

`core/src/test/kotlin/ru/lionzxy/tplauncher/AppInfoTest.kt`: pure-function tests (no
I/O) asserting `AppInfo.startupBanner` contains `BuildConfig.NAME` / `BuildConfig.VERSION`
and the `jvm=`/`os=`/arch fields, and that `AppInfo.version` matches the `N.N.N` scheme
(a regex, not merely `isNotBlank()` — so a broken MAJOR/MINOR wiring that falls back to
Gradle's literal `"unspecified"` is caught). `BuildConfig` is generated for `:core`'s
`main` source set and is on the test compile classpath, so the test can reference it
directly.

Manual verification: run `:core:runCli --no-launch` (or the GUI) and confirm the first
log line reads e.g. `TechnoparkLauncher v2.0.0 (jvm=21.0.11, os=Linux amd64)`.

## Out of scope

- Changing the version scheme or how `MAJOR`/`MINOR` are supplied.
- Migrating build-config tooling to yshrsmz/BuildKonfig.
- Any UI surface for the version (in-window "About"/version label) — this is log-only.
