# Windows connectivity-repair assistant + offline-launch safety net

**Date:** 2026-07-01
**Branch:** `fix/network`
**Status:** Approved design, pending implementation plan

## 1. Problem

On Windows, a user's launch failed with three socket errors that are all the same
OS-level condition surfaced through two HTTP stacks:

- Ktor (NIO) fetch of `jres2.json` and the incremental changelog →
  `java.net.SocketException: Permission denied: getsockopt`
- Legacy `HttpURLConnection` (mclauncher-api) fetch of Mojang's version manifest →
  `java.net.SocketException: Permission denied: connect`

`Permission denied` on `connect`/`getsockopt` is **Windows WSAEACCES (10013)**: the local
OS/security layer refused the JVM's outbound sockets. It is **not** a reachability failure
(that would be `UnknownHostException` / `Connection timed out` / `Connection refused`). The
tell is that it hit **both** the modern Ktor/NIO stack and the legacy `HttpURLConnection`
identically — a client bug cannot do that; something on the machine is blocking the process.

Two independent defects combine:

1. **Environmental (the trigger):** a firewall / antivirus / VPN LSP (or corrupted Winsock
   catalog) blocks the launcher's JVM. On this machine the prime suspect is a third-party AV.
2. **Code (why it's fatal):** even though the JRE was already installed and there were no
   modpack changes to apply, `MinecraftLauncher.getVersion()` insists on an online Mojang
   fetch and only catches `UnknownHostException`. The WSAEACCES `SocketException` escapes and
   aborts launch with a misleading generic "internal error", instead of launching the
   already-installed pack offline.

## 2. Goals / non-goals

**Goals**

- Detect the WSAEACCES block precisely and, on Windows, offer a **guided repair assistant**
  that can programmatically fix the *Windows Firewall* case with a single UAC elevation.
- When the firewall fix cannot restore connectivity, **identify the installed security
  product** and show tailored, copy-pasteable "add an exception" guidance.
- Provide an **offline-launch safety net** so an already-installed modpack still launches
  even if the user declines elevation or the block is un-fixable from code.
- Be **honest**: attempt only what actually works, and verify with a real connectivity probe
  rather than assuming success.

**Non-goals (explicit)**

- No in-app `netsh winsock reset` (destructive: reboot + removes all LSPs). Mentioned in text
  guidance only.
- No modifying third-party AV configuration (impossible and would look like malware).
- Not attempting to delete arbitrary third-party firewall *block* rules.
- No second native helper binary / packaging or code-signing changes.

## 3. Decisions (from brainstorming)

1. **Guided repair assistant** (not a silent "fixes everything" button, not run-whole-app-as-admin).
2. **Include the offline-launch fallback** as a safety net in the same effort.
3. **AV-detect via WMI**, tailored guidance; **no** in-app winsock reset.
4. **Mechanism A:** JNA `ShellExecuteW("runas")` elevating a generated `.cmd` that runs
   `netsh advfirewall firewall add rule`. Zero new dependencies (reuses the existing
   `kernel32`-via-JNA idiom in `WindowsPathHelper`). AV detection is a separate,
   **unprivileged** WMI step.

## 4. Architecture

New code under `core/src/main/kotlin/ru/lionzxy/tplauncher/minecraft/connectivity/`, with a
thin UI layer in `desktop`. OS-touching parts sit behind interfaces; orchestration is pure
and fully unit-testable.

### 4.1 `ConnectivityBlockClassifier` — pure (core)

```kotlin
object ConnectivityBlockClassifier {
    /** True if [t] or any cause is a Windows WSAEACCES (10013) socket block. */
    fun isPermissionDeniedSocket(t: Throwable?): Boolean
}
```
Walks the cause chain; matches `java.net.SocketException` whose lowercased message contains
`"permission denied"`, `"wsaeacces"`, or `"10013"`. Platform-independent. Shared trigger for
both the assistant and the offline messaging.

### 4.2 `WindowsBinaryResolver` — Windows (core)

Resolves the executable paths whose outbound sockets must be allowed (the images Windows
Firewall keys its rules on):

- **Current process image** via `GetModuleFileNameW(null, …)` (new `kernel32` binding, added
  alongside the existing `Kernel32` interface in `WindowsPathHelper` or a sibling).
- `System.getProperty("java.home")` + `\bin\java.exe` and `\bin\javaw.exe` (the launcher's own
  runtime when run from a jar / `gradlew run`).
- The active modpack's bundled JRE `java.exe` and `javaw.exe`, derived from the same location
  `JreManager` resolves for launch.

Returns a deduped list of paths that exist. Non-Windows → empty. Wrapped in try/catch like
`WindowsPathHelper` (JNA load may fail → degrade, never crash).

### 4.3 `FirewallRuleScript` — pure builder (core)

```kotlin
object FirewallRuleScript {
    /** Builds an idempotent .cmd body that allow-lists each binary for outbound traffic. */
    fun build(binaries: List<File>, ruleNamePrefix: String = "TechnoparkLauncher"): String
}
```
For each binary, emits (idempotent):
```
netsh advfirewall firewall delete rule name="TechnoparkLauncher - <n>" >nul 2>&1
netsh advfirewall firewall add rule name="TechnoparkLauncher - <n>" dir=out action=allow program="<path>" enable=yes profile=any
```
followed by writing a completion marker. Program paths are defensively quoted (spaces,
Cyrillic). Paths originate from the OS / our own resolution, never free-text user input.
Pure → assert generated text in unit tests.

### 4.4 `ElevatedRunner` (iface) + `WindowsShellElevatedRunner` (Windows)

```kotlin
sealed interface ElevationResult {
    object Success : ElevationResult
    object UserCancelled : ElevationResult
    data class Failed(val reason: String) : ElevationResult
}
interface ElevatedRunner { fun runElevated(scriptFile: File): ElevationResult }
```
Windows impl writes the script to a file and elevates via
`Shell32.ShellExecuteExW` (verb `runas`, `cmd.exe /c "<script>"`, `SW_HIDE`) with
`SEE_MASK_NOCLOSEPROCESS`, then `WaitForSingleObject` + `GetExitCodeProcess`
(`kernel32`, already bound). UAC decline is detected via `ERROR_CANCELLED (1223)` /
`SE_ERR_ACCESSDENIED`. Note: `ElevationResult.Success` means "the elevated script ran", not
"connectivity is fixed" — the probe (4.5) is the authority on that. Fake-able in tests.

### 4.5 `ConnectivityProbe` (iface) + `KtorConnectivityProbe` (core)

```kotlin
sealed interface ProbeResult { object Reachable; object Blocked; object OtherFailure }
interface ConnectivityProbe { suspend fun probe(): ProbeResult }
```
Impl sends a HEAD (fallback GET) to a cheap known URL (`<BASE_URL>/jres2.json`) via the
hardened `HttpDownloader`, with a **short timeout override** (~8s) so it can't inherit the
5-minute default. **Any** HTTP response (including 4xx) ⇒ `Reachable` (the socket opened).
A WSAEACCES connect exception ⇒ `Blocked`. Anything else ⇒ `OtherFailure`. This is the real
"did the fix work" signal. Fake-able in tests.

### 4.6 `SecurityProductDetector` (iface) + `WindowsWmiSecurityProductDetector` (Windows)

```kotlin
interface SecurityProductDetector { fun detect(): List<String> }
```
Unprivileged (no admin needed):
`powershell -NoProfile -Command "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct | Select-Object -ExpandProperty displayName"`,
stdout captured via `ProcessBuilder`. Best-effort → empty list on any failure. A small
mapping turns known product names (Kaspersky, ESET, Avast/AVG, Dr.Web, Norton, Windows
Defender, …) into tailored "add an exception for `<path>`" guidance, with a generic fallback.

### 4.7 `ConnectivityRepairOrchestrator` — core

```kotlin
sealed interface RepairOutcome {
    object Repaired : RepairOutcome                                   // probe now Reachable → retry launch
    object Cancelled : RepairOutcome                                  // user declined UAC
    data class Guidance(val products: List<String>, val steps: String) : RepairOutcome
}
class ConnectivityRepairOrchestrator(
    private val binaries: WindowsBinaryResolver,
    private val runner: ElevatedRunner,
    private val probe: ConnectivityProbe,
    private val detector: SecurityProductDetector,
) { suspend fun repair(): RepairOutcome }
```
Flow: resolve binaries → `FirewallRuleScript.build` → `runner.runElevated`.
`UserCancelled` → `Cancelled`. Otherwise `probe()`: `Reachable` → `Repaired`; still blocked →
`detector.detect()` → `Guidance`. Pure orchestration over injected interfaces → unit-tested
with fakes for cancel / fixed-by-firewall / still-blocked→guidance paths.

## 5. Data flow & UX

```
launch/login → SocketException(WSAEACCES) deep in stack
  → caught in LauncherViewModel → ConnectivityBlockClassifier.isPermissionDeniedSocket == true
  → LauncherState.ConnectivityBlocked   (NOT the generic internalError)
```

The `ConnectivityBlocked` screen (Windows only) offers:

- Plain-language cause + **"Разрешить доступ (нужны права администратора)"** → orchestrator → UAC.
- `Repaired` → automatically retry the exact failed step (launch or login).
- `Guidance` → show detected AV + copy-pasteable exception steps for `<path>`.
- Whenever the pack is installed: **"Запустить установленную версию"** → offline launch (§6).
- **Retry** and **Cancel**.

Non-Windows: the classifier never fires for WSAEACCES, so this state never appears; the "Fix"
button is Windows-gated regardless.

`LauncherViewModel` changes: in the `onGameStart` and `onLogin` catch blocks, branch on
`isPermissionDeniedSocket(e)` → `ConnectivityBlocked` instead of `internalError`; and stop
Sentry-reporting these environmental (non-bug) errors.

## 6. Offline-launch safety net

`MinecraftLauncher.getVersion()` (`core/.../minecraft/MinecraftLauncher.kt:107-113`):

- Broaden `catch (ex: UnknownHostException)` → `catch (e: IOException)` (covers
  `SocketException`/WSAEACCES, `SocketTimeoutException`, `ConnectException`,
  `UnknownHostException`, `SSLException`).
- Null-guard `versionList.retrieveVersionInfo(...)`: if null, throw a clear "version not
  installed offline and Mojang fetch failed — launch once online to install it" instead of the
  opaque `!!` NPE.

Rationale: `MCDownloadVersionList.retrieveVersionInfo` reads `versions/<id>/<id>.json`
**local-first** (only falling back online when local is null), and `startDownload()` runs the
local disk scan *before* the failing online call. So swallowing the online failure lets an
installed pack resolve its version entirely from disk. Mirrors the existing graceful
degradation in `IncrementalDownloader.init` and `JreManager.fetchJreManifest`.

**Known subtlety to handle in the plan:** `retrieveVersionInfo` → `resolveInheritance`
recursively re-enters `retrieveVersionInfo` for the `inheritsFrom` parent (Forge packs inherit
a vanilla version). For a *fully*-installed pack both JSONs are on disk. For a partially
installed pack line 112 itself can throw a network error — the null-guard alone is
insufficient there. Wrapping the `retrieveVersionInfo` call to map a network failure to the
same clear "not installed offline" message hardens this; guarding the downstream installer
step is deferred (§8).

## 7. Error handling & honest limitations

- **UAC cancelled** → "you declined the permission prompt" + retry / launch-offline.
- **netsh non-zero, JNA load failure, probe timeout** → treated as "not fixed", fall through
  to `Guidance`. The assistant **never aborts the app**; worst case is guidance + offline launch.
- **Limitation 1:** Windows Firewall *block* rules take precedence over allow rules. The netsh
  step fixes the common "default-outbound-block / missing-allow-rule" case and cleans up our
  own prior rules, but cannot safely delete arbitrary third-party block rules.
- **Limitation 2 (important):** the prime suspect here is a third-party AV, which the firewall
  rule will **not** fix. The firewall attempt is still worth it (cheap, fixes the Defender
  subset) — but expect to often land on AV guidance + offline launch. The probe keeps the
  outcome truthful instead of assumed.

## 8. Out of scope (documented follow-ups)

- In-app `netsh winsock reset` (chosen out).
- Guarding `MinecraftDownloader.download`'s unconditional `version.installer.install` for
  *partially*-installed packs so a missing asset offline doesn't re-introduce a fatal fetch
  (Fix 4 from diagnosis).
- Replacing the legacy `HttpURLConnection` Mojang fetch with the hardened Ktor client, or the
  `sun.net.client.default*Timeout` stopgap (Fix 3).

## 9. Testing strategy

**Unit (no real netsh / ShellExecute / WMI — all behind interfaces/fakes):**

- `ConnectivityBlockClassifier`: message variants, nested cause chain, non-socket → false.
- `FirewallRuleScript`: quoting, idempotent delete-then-add, Cyrillic program path.
- AV name → guidance mapping (known products + generic fallback).
- `ConnectivityRepairOrchestrator`: cancel / fixed-by-firewall / still-blocked→guidance, with
  fakes.
- `getVersion` offline fallback: fake version list whose `startDownload` throws
  `SocketException` → local `retrieveVersionInfo` succeeds; null → clear error, not NPE.

**ViewModel:** WSAEACCES → `ConnectivityBlocked` (not `internalError`); `Repaired` → retries
the failed step. Uses the existing ViewModel/state test harness.

**Manual (Windows VM):** add a Defender outbound block for the bundled `javaw.exe` → confirm
the assistant + probe behavior; confirm offline launch of an installed pack with the network
pulled.

## 10. Affected files (indicative)

- New: `core/.../minecraft/connectivity/` — `ConnectivityBlockClassifier`,
  `WindowsBinaryResolver`, `FirewallRuleScript`, `ElevatedRunner` (+ Windows impl),
  `ConnectivityProbe` (+ Ktor impl), `SecurityProductDetector` (+ WMI impl),
  `ConnectivityRepairOrchestrator`.
- Edit: `core/.../utils/WindowsPathHelper.kt` (or sibling) — add `GetModuleFileNameW` binding.
- Edit: `core/.../minecraft/MinecraftLauncher.kt` — offline fallback in `getVersion`.
- Edit: `desktop/.../ui/LauncherViewModel.kt` — WSAEACCES branch → `ConnectivityBlocked`, retry
  wiring, stop Sentry-reporting env errors.
- Edit: `desktop/.../ui/state/LauncherState.kt` (+ Strings) — new `ConnectivityBlocked` state
  and localized messages.
- New tests mirroring §9.
