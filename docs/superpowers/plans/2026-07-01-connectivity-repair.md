# Windows connectivity-repair assistant + offline-launch safety net — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make an already-installed modpack launch when the network is blocked, and add a Windows guided repair assistant that detects the blocking security product (Dr.Web/etc.), auto-fixes the Windows Firewall case via UAC, and gives tailored guidance otherwise.

**Architecture:** OS-touching parts sit behind interfaces in a new `core/.../minecraft/connectivity/` package; orchestration and classification are pure and unit-tested. The UI adds one `LauncherState` and a Compose repair panel. The offline safety net is a small testable refactor of `MinecraftLauncher.getVersion`.

**Tech Stack:** Kotlin 2.4.0, JDK 21 toolchain, Gradle 9.6, Ktor CIO client (`HttpDownloader`), JNA 5.19 (base; custom `StdCallLibrary` interfaces like the existing `WindowsPathHelper.Kernel32`), Compose Desktop, JUnit + Ktor `MockEngine` for tests.

## Global Constraints

- **Build/test JDK:** `export JAVA_HOME=/home/lionzxy/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2` before any `./gradlew` (Gradle 9.6 needs Java 17+; local default `java` is 8).
- **Test command shape:** `./gradlew :core:test --tests "<FQN>" --console=plain` (desktop module: `:desktop:test`).
- **No new dependencies.** JNA base only; declare custom `StdCallLibrary`/`Shell32` interfaces + `Native.load(...)` exactly as `WindowsPathHelper` does for `kernel32`.
- **Windows gating:** all OS-specific code guards on `OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS`; degrade (never crash) on JNA load failure, mirroring `WindowsPathHelper`.
- **UI copy is Russian-first** (matches `Strings`).
- **Package for new code:** `ru.lionzxy.tplauncher.minecraft.connectivity`.
- **Commit after each task.** End commit messages with the `Claude-Session:` trailer.
- **Environment note:** JNA Windows calls, `ShellExecuteExW`, and the WMI PowerShell query cannot be exercised on this Linux CI; those impls are compile-checked here and manually verified on Windows. All pure/interface-based logic IS unit-tested here.

---

## Phase 1 — Offline-launch safety net + WSAEACCES detection (ships the reported-crash fix)

### Task 1: `ConnectivityBlockClassifier` (pure)

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityBlockClassifier.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityBlockClassifierTest.kt`

**Interfaces:**
- Produces: `object ConnectivityBlockClassifier { fun isPermissionDeniedSocket(t: Throwable?): Boolean }`

- [ ] **Step 1: Write the failing test**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class ConnectivityBlockClassifierTest {
    @Test fun detectsPermissionDeniedConnect() =
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Permission denied: connect")))

    @Test fun detectsPermissionDeniedGetsockopt() =
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Permission denied: getsockopt")))

    @Test fun detectsWhenWrappedInCauseChain() =
        assertTrue(ConnectivityBlockClassifier.isPermissionDeniedSocket(
            RuntimeException("prepare failed", IOException("io", SocketException("Permission denied: connect")))))

    @Test fun ignoresUnknownHost() =
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(UnknownHostException("minecraft.glitchless.ru")))

    @Test fun ignoresOtherSocketErrors() =
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(SocketException("Connection reset")))

    @Test fun nullIsFalse() = assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(null))

    @Test fun guardsAgainstSelfReferentialCause() {
        val e = SocketException("Connection reset")
        e.initCause(e) // pathological
        assertFalse(ConnectivityBlockClassifier.isPermissionDeniedSocket(e))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifierTest" --console=plain`
Expected: FAIL — unresolved reference `ConnectivityBlockClassifier`.

- [ ] **Step 3: Write minimal implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import java.net.SocketException

/**
 * Recognises a Windows WSAEACCES (10013) "Permission denied" socket block — a firewall / antivirus
 * (e.g. Dr.Web) / VPN LSP refusing the JVM's outbound sockets. This is NOT a reachability failure
 * (that surfaces as UnknownHostException / connection timeout / refused).
 */
object ConnectivityBlockClassifier {
    fun isPermissionDeniedSocket(t: Throwable?): Boolean {
        var e = t
        val seen = HashSet<Throwable>()
        while (e != null && seen.add(e)) {
            if (e is SocketException) {
                val m = e.message?.lowercase().orEmpty()
                if ("permission denied" in m || "wsaeacces" in m || "10013" in m) return true
            }
            e = e.cause
        }
        return false
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifierTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityBlockClassifier.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityBlockClassifierTest.kt
git commit -m "feat(connectivity): detect Windows WSAEACCES permission-denied socket blocks"
```

### Task 2: `getVersion` offline fallback (the actual fix)

**Files:**
- Modify: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftLauncher.kt:102-114` (+ imports line 14)
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/OfflineVersionFallback.kt` (extracted pure logic)
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/OfflineVersionFallbackTest.kt`

**Interfaces:**
- Consumes: `sk.tomsik68.mclauncher.api.versions.IVersion` (interface, stub-able).
- Produces:
  `class VersionNotInstalledOfflineException(versionId: String) : IOException(...)`
  `fun resolveVersionWithOfflineFallback(versionId: String, startDownload: () -> Unit, retrieve: () -> IVersion?): IVersion`

- [ ] **Step 1: Write the failing test**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import sk.tomsik68.mclauncher.api.versions.IVersion
import java.net.SocketException

private fun stubVersion(): IVersion = object : IVersion {
    override fun getId() = "1.7.10"
    override fun getType() = "release"
    override fun getReleaseTime() = ""
    override fun getUpdateTime() = ""
}

class OfflineVersionFallbackTest {
    @Test fun swallowsSocketExceptionAndReturnsLocalVersion() {
        val local = stubVersion()
        val result = resolveVersionWithOfflineFallback(
            versionId = "1.7.10",
            startDownload = { throw SocketException("Permission denied: connect") },
            retrieve = { local },
        )
        assertSame(local, result)
    }

    @Test fun throwsClearErrorWhenBlockedAndNotInstalled() {
        assertThrows(VersionNotInstalledOfflineException::class.java) {
            resolveVersionWithOfflineFallback(
                versionId = "1.7.10",
                startDownload = { throw SocketException("Permission denied: connect") },
                retrieve = { null },
            )
        }
    }

    @Test fun happyPathReturnsResolvedVersion() {
        val v = stubVersion()
        assertSame(v, resolveVersionWithOfflineFallback("1.7.10", startDownload = {}, retrieve = { v }))
    }
}
```
> Note at execution time: verify `IVersion`'s exact abstract methods from the dependency jar and adjust the stub to match (add any missing overrides). The three tests are the contract; the stub is scaffolding.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.connectivity.OfflineVersionFallbackTest" --console=plain`
Expected: FAIL — unresolved `resolveVersionWithOfflineFallback` / `VersionNotInstalledOfflineException`.

- [ ] **Step 3: Write minimal implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import ru.lionzxy.tplauncher.log.Logger
import sk.tomsik68.mclauncher.api.versions.IVersion
import java.io.IOException

/** Thrown when the version JSON is not on disk AND the online manifest fetch failed. */
class VersionNotInstalledOfflineException(versionId: String) : IOException(
    "Minecraft version '$versionId' is not installed and could not be fetched (network blocked). " +
        "Launch once with a working connection to install it."
)

/**
 * Resolves a Minecraft version, tolerating a blocked/unreachable network. [startDownload] populates
 * the online manifest but is best-effort: any IOException (SocketException/WSAEACCES,
 * SocketTimeoutException, ConnectException, UnknownHostException, SSLException) is swallowed because
 * [retrieve] reads the version JSON from disk first. Throws [VersionNotInstalledOfflineException]
 * when nothing is on disk and the online fetch failed, instead of an opaque NPE.
 */
fun resolveVersionWithOfflineFallback(
    versionId: String,
    startDownload: () -> Unit,
    retrieve: () -> IVersion?,
): IVersion {
    try {
        startDownload()
    } catch (e: IOException) {
        Logger.w("Launcher", "Online version manifest unavailable; using on-disk version list", e)
    }
    return retrieve() ?: throw VersionNotInstalledOfflineException(versionId)
}
```

- [ ] **Step 4: Wire it into `MinecraftLauncher.getVersion` and drop the narrow catch**
Replace `MinecraftLauncher.kt:102-114` with:
```kotlin
    fun getVersion(): IVersion {
        cacheVersion?.let { return it }
        val versionList = MCDownloadVersionList(minecraft.getMinecraftInstance())
        return resolveVersionWithOfflineFallback(
            versionId = minecraft.modpack.version,
            startDownload = { versionList.startDownload() },
            retrieve = { versionList.retrieveVersionInfo(minecraft.modpack.version) },
        ).also { cacheVersion = it }
    }
```
Update imports at the top of the file: remove `import java.net.UnknownHostException` (now unused here — verify with a grep of the file first) and add
`import ru.lionzxy.tplauncher.minecraft.connectivity.resolveVersionWithOfflineFallback`.

- [ ] **Step 5: Run tests + compile the module**
Run: `./gradlew :core:test --tests "ru.lionzxy.tplauncher.minecraft.connectivity.OfflineVersionFallbackTest" --console=plain && ./gradlew :core:compileKotlin --console=plain`
Expected: tests PASS and `:core:compileKotlin` BUILD SUCCESSFUL (confirms `MinecraftLauncher` still compiles, `UnknownHostException` import not dangling).

- [ ] **Step 6: Commit**
```bash
git add core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/OfflineVersionFallback.kt \
        core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/OfflineVersionFallbackTest.kt \
        core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/MinecraftLauncher.kt
git commit -m "fix(launch): launch already-installed modpack offline when version manifest fetch is blocked"
```

### Task 3: ViewModel maps WSAEACCES to an actionable message (not "internal error", no Sentry spam)

**Files:**
- Create: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/LaunchErrorMapper.kt`
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt` (add `connectionBlocked`)
- Modify: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/LauncherViewModel.kt:110-119` (onGameStart catch) and `:93-96` (onLogin IOException catch)
- Test: `desktop/src/test/kotlin/ru/lionzxy/tplauncher/ui/LaunchErrorMapperTest.kt`

**Interfaces:**
- Consumes: `ConnectivityBlockClassifier.isPermissionDeniedSocket`, `LauncherState`, `Strings`.
- Produces:
  `data class LaunchErrorMapping(val state: LauncherState, val reportToSentry: Boolean)`
  `fun mapLaunchError(email: String, e: Throwable): LaunchErrorMapping`

- [ ] **Step 1: Add the string** — in `Strings.kt`, after the `internalError` line:
```kotlin
    const val connectionBlocked =
        "Похоже, антивирус, файрвол или VPN блокирует подключение лаунчера к сети. " +
            "Разрешите доступ в интернет для java.exe и javaw.exe лаунчера в вашем антивирусе " +
            "и в брандмауэре Windows, затем повторите попытку."
```

- [ ] **Step 2: Write the failing test**
```kotlin
package ru.lionzxy.tplauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.state.LauncherState
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class LaunchErrorMapperTest {
    @Test fun permissionDeniedMapsToConnectionBlockedNoSentry() {
        val m = mapLaunchError("a@b.c", SocketException("Permission denied: connect"))
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.connectionBlocked), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test fun unknownHostMapsToCheckInternetNoSentry() {
        val m = mapLaunchError("a@b.c", UnknownHostException("host"))
        assertEquals(LauncherState.InitialError(Strings.checkInternetConnection), m.state)
        assertFalse(m.reportToSentry)
    }

    @Test fun otherErrorsMapToInternalAndReport() {
        val m = mapLaunchError("a@b.c", IllegalStateException("boom"))
        assertEquals(LauncherState.LaunchError("a@b.c", Strings.internalError), m.state)
        assertTrue(m.reportToSentry)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**
Run: `./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.ui.LaunchErrorMapperTest" --console=plain`
Expected: FAIL — unresolved `mapLaunchError`.

- [ ] **Step 4: Write minimal implementation** (`LaunchErrorMapper.kt`)
```kotlin
package ru.lionzxy.tplauncher.ui

import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier
import ru.lionzxy.tplauncher.ui.state.LauncherState
import java.net.UnknownHostException

data class LaunchErrorMapping(val state: LauncherState, val reportToSentry: Boolean)

/**
 * Classifies a launch/prepare failure into a user-facing state. WSAEACCES firewall/AV blocks and
 * plain no-network are environmental (not bugs): they get actionable messages and are NOT sent to
 * Sentry. Everything else is an unexpected error worth reporting.
 */
fun mapLaunchError(email: String, e: Throwable): LaunchErrorMapping = when {
    ConnectivityBlockClassifier.isPermissionDeniedSocket(e) ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.connectionBlocked), reportToSentry = false)
    e is UnknownHostException ->
        LaunchErrorMapping(LauncherState.InitialError(Strings.checkInternetConnection), reportToSentry = false)
    else ->
        LaunchErrorMapping(LauncherState.LaunchError(email, Strings.internalError), reportToSentry = true)
}
```

- [ ] **Step 5: Use it in `onGameStart`** — replace the two catch blocks (`LauncherViewModel.kt:110-119`) with a single:
```kotlin
        } catch (e: Exception) {
            Logger.e("Launcher", "Failed to prepare/launch Minecraft", e)
            val mapping = mapLaunchError(email, e)
            if (mapping.reportToSentry) Sentry.captureException(e)
            _state.value = mapping.state
            return
        }
```
And in `onLogin`'s `catch (ioExp: IOException)` (`:93-96`), branch so a WSAEACCES login block shows `connectionBlocked`:
```kotlin
            } catch (ioExp: IOException) {
                Logger.e("Login", "Network error during login", ioExp)
                _state.value = if (ConnectivityBlockClassifier.isPermissionDeniedSocket(ioExp))
                    LauncherState.InitialError(Strings.connectionBlocked)
                else LauncherState.InitialError(Strings.checkInternetConnection)
                return@launch
            }
```
Add imports: `import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier`. Keep the existing `UnknownHostException` import (still used by the mapper indirectly? No — remove from ViewModel only if now unused; grep first).

- [ ] **Step 6: Run test + compile desktop**
Run: `./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.ui.LaunchErrorMapperTest" --console=plain && ./gradlew :desktop:compileKotlin --console=plain`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**
```bash
git add desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/LaunchErrorMapper.kt \
        desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/Strings.kt \
        desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/LauncherViewModel.kt \
        desktop/src/test/kotlin/ru/lionzxy/tplauncher/ui/LaunchErrorMapperTest.kt
git commit -m "feat(ui): show firewall/AV-block message (not internal error) and stop Sentry-reporting env blocks"
```

**Phase 1 exit criteria:** `./gradlew :core:test :desktop:test --console=plain` green; the reported crash is fixed (an installed pack launches when blocked; a not-installed pack shows `connectionBlocked`). Ship-ready.

---

## Phase 2 — Connectivity-repair core (pure/injectable; no OS side effects in tests)

### Task 4: `FirewallRuleScript` (pure builder)

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/FirewallRuleScript.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/FirewallRuleScriptTest.kt`

**Interfaces:**
- Produces: `object FirewallRuleScript { fun build(binaries: List<File>, markerFile: File, ruleNamePrefix: String = "TechnoparkLauncher"): String }`

- [ ] **Step 1: Write the failing test**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirewallRuleScriptTest {
    private val marker = File("C:\\tmp\\repair.done")

    @Test fun addsOutboundAllowRulePerBinary() {
        val s = FirewallRuleScript.build(listOf(File("C:\\Games\\javaw.exe")), marker)
        assertTrue(s.contains("advfirewall firewall add rule"))
        assertTrue(s.contains("dir=out action=allow"))
        assertTrue(s.contains("program=\"C:\\Games\\javaw.exe\""))
    }

    @Test fun isIdempotentDeleteThenAdd() {
        val s = FirewallRuleScript.build(listOf(File("C:\\Games\\javaw.exe")), marker)
        val del = s.indexOf("advfirewall firewall delete rule")
        val add = s.indexOf("advfirewall firewall add rule")
        assertTrue(del in 0 until add) // delete precedes add
    }

    @Test fun quotesCyrillicAndSpacedPaths() {
        val s = FirewallRuleScript.build(listOf(File("C:\\Пользователи\\Конст Games\\javaw.exe")), marker)
        assertTrue(s.contains("program=\"C:\\Пользователи\\Конст Games\\javaw.exe\""))
    }

    @Test fun writesMarkerLast() {
        val s = FirewallRuleScript.build(listOf(File("C:\\a\\javaw.exe")), marker)
        assertTrue(s.trimEnd().endsWith("\"${marker.absolutePath}\"") || s.contains(marker.absolutePath))
        assertTrue(s.lastIndexOf(marker.absolutePath) > s.lastIndexOf("add rule"))
    }

    @Test fun oneRulePerBinaryDistinctNames() {
        val s = FirewallRuleScript.build(listOf(File("C:\\a\\java.exe"), File("C:\\a\\javaw.exe")), marker)
        assertTrue(s.contains("TechnoparkLauncher 1"))
        assertTrue(s.contains("TechnoparkLauncher 2"))
    }
}
```

- [ ] **Step 2: Run to verify FAIL** — `./gradlew :core:test --tests "*FirewallRuleScriptTest" --console=plain` → unresolved `FirewallRuleScript`.

- [ ] **Step 3: Implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/**
 * Builds a batch (`.cmd`) body that allow-lists each binary for OUTBOUND traffic in Windows Firewall.
 * Idempotent: deletes any prior same-named rule before adding. Program paths are wrapped in double
 * quotes (safe for spaces and Cyrillic). The last line touches [markerFile] so the caller can confirm
 * the elevated script ran to completion. Rule NAMES are our own constant text (never interpolated
 * from untrusted input); only OS-resolved program paths are interpolated, and they are quoted.
 */
object FirewallRuleScript {
    fun build(binaries: List<File>, markerFile: File, ruleNamePrefix: String = "TechnoparkLauncher"): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        binaries.forEachIndexed { i, bin ->
            val name = "$ruleNamePrefix ${i + 1}"
            sb.appendLine("netsh advfirewall firewall delete rule name=\"$name\" >nul 2>&1")
            sb.appendLine(
                "netsh advfirewall firewall add rule name=\"$name\" dir=out action=allow " +
                    "program=\"${bin.absolutePath}\" enable=yes profile=any"
            )
        }
        // Marker written last so its presence == script completed.
        sb.appendLine("echo done> \"${markerFile.absolutePath}\"")
        return sb.toString()
    }
}
```
- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `feat(connectivity): build idempotent netsh firewall allow-rule script`.

### Task 5: `SecurityProduct` classification (pure mapping) + detector interface

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/SecurityProductDetector.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/SecurityProductClassifierTest.kt`

**Interfaces:**
- Produces:
  `enum class AvClass { THIRD_PARTY_NETWORK, DEFENDER_ONLY, NONE_DETECTED }`
  `data class DetectedSecurity(val products: List<String>, val avClass: AvClass)`
  `interface SecurityProductDetector { fun detect(): DetectedSecurity }`
  `object SecurityProductClassifier { fun classify(displayNames: List<String>): DetectedSecurity }`

- [ ] **Step 1: Write the failing test**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityProductClassifierTest {
    @Test fun drwebIsThirdPartyNetwork() =
        assertEquals(AvClass.THIRD_PARTY_NETWORK, SecurityProductClassifier.classify(listOf("Dr.Web Security Space")).avClass)

    @Test fun kasperskyIsThirdPartyNetwork() =
        assertEquals(AvClass.THIRD_PARTY_NETWORK, SecurityProductClassifier.classify(listOf("Kaspersky Internet Security")).avClass)

    @Test fun defenderOnlyIsDefender() =
        assertEquals(AvClass.DEFENDER_ONLY, SecurityProductClassifier.classify(listOf("Windows Defender")).avClass)

    @Test fun emptyIsNoneDetected() =
        assertEquals(AvClass.NONE_DETECTED, SecurityProductClassifier.classify(emptyList()).avClass)

    @Test fun defenderPlusThirdPartyIsThirdParty() =
        assertEquals(AvClass.THIRD_PARTY_NETWORK,
            SecurityProductClassifier.classify(listOf("Windows Defender", "Dr.Web")).avClass)

    @Test fun preservesProductNames() =
        assertEquals(listOf("Dr.Web"), SecurityProductClassifier.classify(listOf("Dr.Web")).products)
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

enum class AvClass { THIRD_PARTY_NETWORK, DEFENDER_ONLY, NONE_DETECTED }

data class DetectedSecurity(val products: List<String>, val avClass: AvClass)

interface SecurityProductDetector {
    /** Installed AV products + classification. Best-effort; [DetectedSecurity] with NONE_DETECTED on failure. */
    fun detect(): DetectedSecurity
}

/** Pure name → class mapping, unit-tested independently of the WMI query. */
object SecurityProductClassifier {
    private val THIRD_PARTY = listOf("dr.web", "drweb", "kaspersky", "eset", "nod32", "avast", "avg", "norton", "comodo")

    fun classify(displayNames: List<String>): DetectedSecurity {
        val products = displayNames.map { it.trim() }.filter { it.isNotEmpty() }
        val lower = products.map { it.lowercase() }
        return when {
            lower.any { name -> THIRD_PARTY.any { it in name } } -> DetectedSecurity(products, AvClass.THIRD_PARTY_NETWORK)
            products.isEmpty() -> DetectedSecurity(products, AvClass.NONE_DETECTED)
            else -> DetectedSecurity(products, AvClass.DEFENDER_ONLY)
        }
    }
}
```
- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `feat(connectivity): classify installed AV product into fixability class`.

### Task 6: `ConnectivityProbe` interface + `KtorConnectivityProbe` (MockEngine-tested)

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityProbe.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/KtorConnectivityProbeTest.kt`

**Interfaces:**
- Consumes: `io.ktor.client.HttpClient`, `applyDefaults()` pattern (see `HttpDownloaderTest` for MockEngine usage), `ConnectivityBlockClassifier`.
- Produces:
  `enum class ProbeResult { REACHABLE, BLOCKED, OTHER_FAILURE }`
  `interface ConnectivityProbe { suspend fun probe(): ProbeResult }`
  `class KtorConnectivityProbe(private val client: HttpClient, private val url: String) : ConnectivityProbe`

- [ ] **Step 1: Write the failing test** (mirror MockEngine setup from `desktop`/`core` `HttpDownloaderTest`)
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketException

class KtorConnectivityProbeTest {
    private fun probeWith(engine: MockEngine) =
        KtorConnectivityProbe(HttpClient(engine), "https://minecraft.glitchless.ru/jres2.json")

    @Test fun anyHttpResponseIsReachable() = runBlocking {
        val p = probeWith(MockEngine { respond("ok", HttpStatusCode.Forbidden) }) // even 403 == socket opened
        assertEquals(ProbeResult.REACHABLE, p.probe())
    }

    @Test fun permissionDeniedIsBlocked() = runBlocking {
        val p = probeWith(MockEngine { throw SocketException("Permission denied: connect") })
        assertEquals(ProbeResult.BLOCKED, p.probe())
    }

    @Test fun otherErrorIsOtherFailure() = runBlocking {
        val p = probeWith(MockEngine { throw SocketException("Connection reset") })
        assertEquals(ProbeResult.OTHER_FAILURE, p.probe())
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import ru.lionzxy.tplauncher.log.Logger

enum class ProbeResult { REACHABLE, BLOCKED, OTHER_FAILURE }

interface ConnectivityProbe {
    suspend fun probe(): ProbeResult
}

/**
 * Opens a socket to [url]; ANY HTTP response (even 4xx) means the connection succeeded → REACHABLE.
 * A WSAEACCES connect exception → BLOCKED; any other failure → OTHER_FAILURE. Inject a short-timeout
 * [client] (production wiring uses a dedicated CIO client with an ~8s connect/request cap so a probe
 * can't inherit the downloader's 5-minute window).
 */
class KtorConnectivityProbe(private val client: HttpClient, private val url: String) : ConnectivityProbe {
    override suspend fun probe(): ProbeResult = try {
        client.head(url)
        ProbeResult.REACHABLE
    } catch (e: Throwable) {
        if (ConnectivityBlockClassifier.isPermissionDeniedSocket(e)) {
            ProbeResult.BLOCKED
        } else {
            // A non-2xx with expectSuccess would also land here; treat as reachable only if the
            // socket opened. To keep the contract simple we classify unknown throwables as failure.
            Logger.w("Connectivity", "Probe to $url failed (non-permission)", e)
            ProbeResult.OTHER_FAILURE
        }
    }
}
```
> Execution note: the MockEngine test client has `expectSuccess` unset (default false), so a 403 returns normally → REACHABLE, matching the test. The production probe client should set `expectSuccess = false` so an HTTP error status is still "socket opened = reachable".

- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `feat(connectivity): Ktor connectivity probe (any HTTP response = reachable)`.

### Task 7: `ElevatedRunner`/`WindowsBinaryResolver` interfaces + pure candidate-path assembly

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ElevatedRunner.kt`
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/BinaryCandidates.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/BinaryCandidatesTest.kt`

**Interfaces:**
- Produces:
  `sealed interface ElevationResult { object Success; object UserCancelled; data class Failed(val reason: String) }`
  `interface ElevatedRunner { fun runElevated(scriptFile: File): ElevationResult }`
  `interface BinaryResolver { fun resolve(): List<File> }`
  `fun assembleBinaryCandidates(processImage: File?, javaHome: File?, bundledJreBinDir: File?, exists: (File) -> Boolean): List<File>` (pure; deduped; existing-only)

- [ ] **Step 1: Write the failing test** (pure assembly; inject `exists`)
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BinaryCandidatesTest {
    @Test fun includesJavaAndJavawFromJavaHomeAndBundledDir() {
        val all = assembleBinaryCandidates(
            processImage = File("C:\\App\\launcher.exe"),
            javaHome = File("C:\\App\\rt"),
            bundledJreBinDir = File("C:\\Games\\jre\\bin"),
            exists = { true },
        ).map { it.path }
        assertTrue(all.contains("C:\\App\\launcher.exe"))
        assertTrue(all.contains("C:\\App\\rt\\bin\\java.exe"))
        assertTrue(all.contains("C:\\App\\rt\\bin\\javaw.exe"))
        assertTrue(all.contains("C:\\Games\\jre\\bin\\java.exe"))
        assertTrue(all.contains("C:\\Games\\jre\\bin\\javaw.exe"))
    }

    @Test fun dropsNonExistentAndDedupes() {
        val out = assembleBinaryCandidates(
            processImage = null,
            javaHome = File("C:\\rt"),
            bundledJreBinDir = File("C:\\rt\\bin"), // same java.exe/javaw.exe as javaHome\bin
            exists = { it.path.endsWith("javaw.exe") }, // only javaw exists
        )
        assertEquals(listOf(File("C:\\rt\\bin\\javaw.exe")), out)
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implementation** — `ElevatedRunner.kt`:
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

sealed interface ElevationResult {
    object Success : ElevationResult          // the elevated script ran (NOT "connectivity fixed" — probe decides)
    object UserCancelled : ElevationResult    // user declined the UAC prompt
    data class Failed(val reason: String) : ElevationResult
}

interface ElevatedRunner {
    fun runElevated(scriptFile: File): ElevationResult
}

interface BinaryResolver {
    /** Executable images whose outbound sockets should be allow-listed. Empty on non-Windows. */
    fun resolve(): List<File>
}
```
`BinaryCandidates.kt`:
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/** Pure assembly of allow-list candidates. Deduped by absolute path, keeps only those [exists] accepts. */
fun assembleBinaryCandidates(
    processImage: File?,
    javaHome: File?,
    bundledJreBinDir: File?,
    exists: (File) -> Boolean,
): List<File> {
    val out = LinkedHashSet<File>()
    processImage?.let { out.add(it) }
    listOfNotNull(javaHome?.let { File(it, "bin") }, bundledJreBinDir).forEach { dir ->
        out.add(File(dir, "java.exe"))
        out.add(File(dir, "javaw.exe"))
    }
    return out.filter(exists)
}
```
- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `feat(connectivity): elevation/binary-resolver interfaces + pure allow-list candidate assembly`.

### Task 8: `ConnectivityRepairOrchestrator` (state machine over fakes)

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityRepairOrchestrator.kt`
- Test: `core/src/test/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/ConnectivityRepairOrchestratorTest.kt`

**Interfaces:**
- Consumes: `BinaryResolver`, `ElevatedRunner`, `ConnectivityProbe`, `SecurityProductDetector`, `FirewallRuleScript`, `AvClass`, `ElevationResult`, `ProbeResult`.
- Produces:
  `data class Assessment(val products: List<String>, val firewallFixFirst: Boolean, val avClass: AvClass)`
  `sealed interface RepairOutcome { object Repaired; object Cancelled; data class Guidance(val products: List<String>) }`
  `class ConnectivityRepairOrchestrator(binaries, runner, probe, detector, scriptDir: File) { fun assess(): Assessment; suspend fun tryFirewallFix(): RepairOutcome }`

- [ ] **Step 1: Write the failing test** (fakes only; no OS calls)
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeDetector(val d: DetectedSecurity) : SecurityProductDetector { override fun detect() = d }
private class FakeRunner(val r: ElevationResult) : ElevatedRunner {
    var called = false
    override fun runElevated(scriptFile: File): ElevationResult { called = true; return r }
}
private class FakeProbe(val r: ProbeResult) : ConnectivityProbe { override suspend fun probe() = r }
private class FakeBins(val list: List<File>) : BinaryResolver { override fun resolve() = list }

class ConnectivityRepairOrchestratorTest {
    private fun orch(det: DetectedSecurity, runner: FakeRunner, probe: ProbeResult) =
        ConnectivityRepairOrchestrator(
            FakeBins(listOf(File("C:\\a\\javaw.exe"))), runner, FakeProbe(probe),
            FakeDetector(det), File(System.getProperty("java.io.tmpdir")),
        )

    @Test fun thirdPartyAvDoesNotLeadWithFirewall() {
        val a = orch(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK), FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED).assess()
        assertFalse(a.firewallFixFirst)
        assertEquals(listOf("Dr.Web"), a.products)
    }

    @Test fun defenderLeadsWithFirewall() {
        val a = orch(DetectedSecurity(listOf("Windows Defender"), AvClass.DEFENDER_ONLY), FakeRunner(ElevationResult.Success), ProbeResult.REACHABLE).assess()
        assertTrue(a.firewallFixFirst)
    }

    @Test fun firewallFixReachableIsRepaired() = runBlocking {
        val r = FakeRunner(ElevationResult.Success)
        val out = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), r, ProbeResult.REACHABLE).tryFirewallFix()
        assertTrue(r.called)
        assertEquals(RepairOutcome.Repaired, out)
    }

    @Test fun firewallCancelledIsCancelled() = runBlocking {
        val out = orch(DetectedSecurity(emptyList(), AvClass.NONE_DETECTED), FakeRunner(ElevationResult.UserCancelled), ProbeResult.BLOCKED).tryFirewallFix()
        assertEquals(RepairOutcome.Cancelled, out)
    }

    @Test fun firewallRanButStillBlockedGivesGuidance() = runBlocking {
        val out = orch(DetectedSecurity(listOf("Dr.Web"), AvClass.THIRD_PARTY_NETWORK), FakeRunner(ElevationResult.Success), ProbeResult.BLOCKED).tryFirewallFix()
        assertTrue(out is RepairOutcome.Guidance)
    }
}
```

- [ ] **Step 2: Run to verify FAIL.**
- [ ] **Step 3: Implementation**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

data class Assessment(val products: List<String>, val firewallFixFirst: Boolean, val avClass: AvClass)

sealed interface RepairOutcome {
    object Repaired : RepairOutcome
    object Cancelled : RepairOutcome
    data class Guidance(val products: List<String>) : RepairOutcome
}

/**
 * Detect-first repair flow. [assess] (cheap, unprivileged) decides whether to lead with the firewall
 * fix (Defender / none) or with product guidance (third-party AV like Dr.Web). [tryFirewallFix]
 * elevates once and verifies with a probe.
 */
class ConnectivityRepairOrchestrator(
    private val binaries: BinaryResolver,
    private val runner: ElevatedRunner,
    private val probe: ConnectivityProbe,
    private val detector: SecurityProductDetector,
    private val scriptDir: File,
) {
    private var lastDetected: DetectedSecurity = DetectedSecurity(emptyList(), AvClass.NONE_DETECTED)

    fun assess(): Assessment {
        val d = detector.detect().also { lastDetected = it }
        val firewallFirst = d.avClass != AvClass.THIRD_PARTY_NETWORK
        return Assessment(d.products, firewallFirst, d.avClass)
    }

    suspend fun tryFirewallFix(): RepairOutcome {
        val bins = binaries.resolve()
        val marker = File(scriptDir, "tp-connectivity-repair.done").also { it.delete() }
        val script = File(scriptDir, "tp-connectivity-repair.cmd")
        script.writeText(FirewallRuleScript.build(bins, marker))
        return when (runner.runElevated(script)) {
            is ElevationResult.UserCancelled -> RepairOutcome.Cancelled
            else -> if (probe.probe() == ProbeResult.REACHABLE) RepairOutcome.Repaired
                    else RepairOutcome.Guidance(lastDetected.products)
        }
    }
}
```
- [ ] **Step 4: Run to verify PASS.**
- [ ] **Step 5: Commit** — `feat(connectivity): detect-first repair orchestrator`.

**Phase 2 exit criteria:** `./gradlew :core:test --console=plain` green; all repair logic is exercised by fakes; no OS side effects in tests.

---

## Phase 3 — Windows JNA impls + Compose repair UI (compiles here; behaviour verified on Windows)

> These tasks are Windows-only at runtime. On this Linux machine they are **compile-checked**
> (`./gradlew :core:compileKotlin :desktop:compileKotlin`) and manually verified on a Windows VM
> (add a Defender outbound block for `javaw.exe`; confirm the assistant, probe, and offline launch).
> Snapshot tests for new UI require regenerating baselines with `roborazzi` (see existing
> `snapshot/*Test.kt`); record new baselines rather than asserting pre-existing ones.

### Task 9: JNA Windows implementations

**Files:**
- Create: `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/WindowsConnectivity.kt`
  (holds: `GetModuleFileNameW` current-process-image resolver, `WindowsBinaryResolver : BinaryResolver`,
  `WindowsShellElevatedRunner : ElevatedRunner` via `Shell32.ShellExecuteExW` + `WaitForSingleObject`,
  `WindowsWmiSecurityProductDetector : SecurityProductDetector`).

**Interfaces:**
- Consumes: `BinaryResolver`, `ElevatedRunner`, `SecurityProductDetector`, `ElevationResult`,
  `SecurityProductClassifier`, `assembleBinaryCandidates`, `JreManager` (bundled JRE `bin` dir).
- Produces the three named production impls above.

- [ ] **Step 1: Implement (no unit test — Windows-only; guard on OS, degrade on JNA failure like `WindowsPathHelper`)**
```kotlin
package ru.lionzxy.tplauncher.minecraft.connectivity

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import nu.redpois0n.oslib.OperatingSystem
import ru.lionzxy.tplauncher.log.Logger
import java.io.File

private val isWindows get() = OperatingSystem.getOperatingSystem().type == OperatingSystem.WINDOWS

// --- current process image via GetModuleFileNameW(null) ---
private interface Kernel32Ext : StdCallLibrary {
    fun GetModuleFileNameW(hModule: Pointer?, lpFilename: CharArray, nSize: Int): Int
    fun WaitForSingleObject(hHandle: Pointer, dwMilliseconds: Int): Int
    fun GetExitCodeProcess(hProcess: Pointer, lpExitCode: IntArray): Boolean
    fun CloseHandle(hObject: Pointer): Boolean
}
private val kernel32Ext: Kernel32Ext? by lazy {
    if (!isWindows) null else runCatching { Native.load("kernel32", Kernel32Ext::class.java) }
        .onFailure { Logger.w("Connectivity", "kernel32 load failed", it) }.getOrNull()
}
internal fun currentProcessImage(): File? {
    val k = kernel32Ext ?: return null
    return runCatching {
        val buf = CharArray(1024)
        val len = k.GetModuleFileNameW(null, buf, buf.size)
        if (len in 1 until buf.size) File(String(buf, 0, len)) else null
    }.getOrNull()
}

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

// --- ShellExecuteExW runas ---
@Structure.FieldOrder("cbSize","fMask","hwnd","lpVerb","lpFile","lpParameters","lpDirectory","nShow","hInstApp","lpIDList","lpClass","hkeyClass","dwHotKey","hIconOrMonitor","hProcess")
internal class ShellExecuteInfo : Structure() {
    @JvmField var cbSize = 0
    @JvmField var fMask = 0
    @JvmField var hwnd: Pointer? = null
    @JvmField var lpVerb: WString? = null
    @JvmField var lpFile: WString? = null
    @JvmField var lpParameters: WString? = null
    @JvmField var lpDirectory: WString? = null
    @JvmField var nShow = 0
    @JvmField var hInstApp: Pointer? = null
    @JvmField var lpIDList: Pointer? = null
    @JvmField var lpClass: WString? = null
    @JvmField var hkeyClass: Pointer? = null
    @JvmField var dwHotKey = 0
    @JvmField var hIconOrMonitor: Pointer? = null
    @JvmField var hProcess: Pointer? = null
}
private interface Shell32 : StdCallLibrary {
    fun ShellExecuteExW(info: ShellExecuteInfo): Boolean
}
private val shell32: Shell32? by lazy {
    if (!isWindows) null else runCatching { Native.load("shell32", Shell32::class.java) }
        .onFailure { Logger.w("Connectivity", "shell32 load failed", it) }.getOrNull()
}

class WindowsShellElevatedRunner : ElevatedRunner {
    private val SEE_MASK_NOCLOSEPROCESS = 0x00000040
    private val SW_HIDE = 0
    private val ERROR_CANCELLED = 1223

    override fun runElevated(scriptFile: File): ElevationResult {
        val shell = shell32 ?: return ElevationResult.Failed("shell32 unavailable")
        val k = kernel32Ext ?: return ElevationResult.Failed("kernel32 unavailable")
        val info = ShellExecuteInfo().apply {
            cbSize = size()
            fMask = SEE_MASK_NOCLOSEPROCESS
            lpVerb = WString("runas")
            lpFile = WString("cmd.exe")
            lpParameters = WString("/c \"${scriptFile.absolutePath}\"")
            nShow = SW_HIDE
        }
        val ok = runCatching { shell.ShellExecuteExW(info) }.getOrElse {
            Logger.w("Connectivity", "ShellExecuteExW threw", it); return ElevationResult.Failed(it.message ?: "shellexecute error")
        }
        if (!ok) {
            return if (Native.getLastError() == ERROR_CANCELLED) ElevationResult.UserCancelled
                   else ElevationResult.Failed("ShellExecuteExW failed: ${Native.getLastError()}")
        }
        val h = info.hProcess ?: return ElevationResult.Success // no handle → assume launched
        return try {
            k.WaitForSingleObject(h, 60_000)
            ElevationResult.Success
        } finally {
            k.CloseHandle(h)
        }
    }
}

class WindowsWmiSecurityProductDetector : SecurityProductDetector {
    override fun detect(): DetectedSecurity {
        if (!isWindows) return DetectedSecurity(emptyList(), AvClass.NONE_DETECTED)
        val names = runCatching {
            val pb = ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | " +
                    "Select-Object -ExpandProperty displayName",
            ).redirectErrorStream(true)
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrElse {
            Logger.w("Connectivity", "AV detection failed", it); emptyList()
        }
        return SecurityProductClassifier.classify(names)
    }
}
```

- [ ] **Step 2: Compile-check both modules**
Run: `./gradlew :core:compileKotlin --console=plain`
Expected: BUILD SUCCESSFUL.
> Execution note: confirm the exact `JreManager` API for the active modpack's bundled JRE `bin` directory (see `JreManager.resolveJavaBinary`/`launchBinary`) and pass its parent `bin` dir into `WindowsBinaryResolver` at the ViewModel wiring in Task 10.

- [ ] **Step 3: Commit** — `feat(connectivity): Windows JNA impls (ShellExecuteExW elevation, WMI AV detect, process-image resolver)`.

### Task 10: `ConnectivityBlocked` state + repair panel + ViewModel wiring

**Files:**
- Modify: `desktop/.../ui/state/LauncherState.kt` (add state + `flags` branch)
- Modify: `desktop/.../ui/Strings.kt` (repair-panel strings + Dr.Web guidance)
- Modify: `desktop/.../ui/LauncherViewModel.kt` (build orchestrator; `onGameStart`/`onLogin` set `ConnectivityBlocked` on WSAEACCES; `onRepairClick()`, `onLaunchOfflineClick()`)
- Create: `desktop/.../ui/components/ConnectivityRepairPanel.kt` (Compose)
- Modify: the main screen composable in `desktop/.../Main.kt` to render the panel when `state is ConnectivityBlocked`
- Test: `desktop/.../ui/state/LauncherStateTest.kt` (flags for new state), `desktop/.../ui/LauncherViewModelTest.kt` (WSAEACCES → ConnectivityBlocked mapping via a seam)

**Interfaces:**
- Consumes: `ConnectivityRepairOrchestrator`, `Assessment`, `RepairOutcome`, `mapLaunchError`.
- Produces: `data class ConnectivityBlocked(val email: String, val assessment: Assessment) : LauncherState()`.

- [ ] **Step 1: Add the state + exhaustive `flags` branch (test first)**
Add to `LauncherStateTest.kt`:
```kotlin
    @Test fun connectivityBlockedFlags_showsMessageAndEnablesButton() {
        val s = LauncherState.ConnectivityBlocked("a@b.c",
            ru.lionzxy.tplauncher.minecraft.connectivity.Assessment(listOf("Dr.Web"), false,
                ru.lionzxy.tplauncher.minecraft.connectivity.AvClass.THIRD_PARTY_NETWORK))
        assertEquals(false, s.flags.buttonDisable)
        assertEquals(TpColors.error, s.flags.titleColor)
    }
```
Then add to `LauncherState.kt`:
```kotlin
    data class ConnectivityBlocked(
        val email: String,
        val assessment: ru.lionzxy.tplauncher.minecraft.connectivity.Assessment,
    ) : LauncherState()
```
and a `flags` branch (keeps the `when` exhaustive — REQUIRED or the module won't compile):
```kotlin
        is LauncherState.ConnectivityBlocked -> StateFlags(
            titleColor = TpColors.error,
            loginPasswordVisible = false,
            successLoginVisible = true,
            progressTextColor = TpColors.error,
            progressTextContent = Strings.connectionBlocked,
            buttonDisable = false,
            buttonText = Strings.retry,
            successLoginText = email,
        )
```
Add `const val retry = "Повторить"` (+ repair-panel strings) to `Strings.kt`.

- [ ] **Step 2: Run `:desktop:test` for `LauncherStateTest`; verify FAIL then implement then PASS.** Run: `./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.ui.state.LauncherStateTest" --console=plain`.

- [ ] **Step 3: ViewModel wiring** — construct a `ConnectivityRepairOrchestrator` (production impls from Task 9, probe client with ~8s timeout, `scriptDir = ConfigHelper` cache dir); in `onGameStart`/`onLogin`, when `ConnectivityBlockClassifier.isPermissionDeniedSocket(e)` **and** the pack is not launchable offline, set `LauncherState.ConnectivityBlocked(email, orchestrator.assess())`. Add `onRepairClick()` (calls `orchestrator.tryFirewallFix()`, on `Repaired` re-runs `onGameStart`, on `Cancelled`/`Guidance` updates state) and `onLaunchOfflineClick()` (re-runs `onGameStart` — the Task-2 fallback makes it succeed when installed). Extract the exception→state decision into a testable function and assert it in `LauncherViewModelTest`.

- [ ] **Step 4: Compose `ConnectivityRepairPanel`** — renders `assessment.products` + Dr.Web/generic guidance, a primary button (firewall fix if `assessment.firewallFixFirst`, else offline launch), a secondary action, and copy-pasteable exception steps. Wire into `Main.kt`'s screen `when (state)`.

- [ ] **Step 5: Compile + snapshot** — `./gradlew :desktop:compileKotlin --console=plain`; record snapshot baselines for the new panel (`./gradlew :desktop:test -Proborazzi.record` or the project's record task — confirm from existing snapshot config).

- [ ] **Step 6: Commit** — `feat(ui): connectivity-blocked repair panel with detect-first flow and offline launch`.

**Phase 3 exit criteria:** both modules compile; core tests green; manual Windows verification of elevation + probe + offline launch documented in the PR.

---

## Self-review

- **Spec coverage:** §4.1→T1; §6→T2; §5 message + Sentry→T3; §4.3→T4; §4.6→T5+T9; §4.5→T6; §4.4/§4.2 (interfaces + pure assembly)→T7, (Windows impls)→T9; §4.7→T8; §5 state+UX→T10. All spec sections map to a task.
- **Placeholder scan:** every code step has complete code; Windows-only impls (T9) and Compose panel (T10 steps 3-4) are described with concrete signatures and, for T9, full code — flagged as compile-checked-here / Windows-verified, which is a real environment constraint, not a placeholder.
- **Type consistency:** `ElevationResult`/`ProbeResult`/`AvClass`/`DetectedSecurity`/`RepairOutcome`/`Assessment` names are used identically across T5–T10; `assembleBinaryCandidates` and `SecurityProductClassifier.classify` signatures match between definition (T5/T7) and use (T9).
- **Known execution-time checks (called out inline):** exact `IVersion` abstract methods for the T2 stub; exact `JreManager` bundled-JRE `bin` dir accessor for T9/T10; the project's roborazzi record task name for T10.
