# Compose Migration — Plan 2: `:desktop` Compose UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `:desktop` Jetpack Compose for Desktop module — a pixel-faithful clone of the legacy JavaFX/TornadoFX launcher UI — on top of the completed `:core` library, validated by headless snapshot rendering against the `img/Screen*` reference mockups, and packaged as the same cross-platform fat jar the self-update pipeline expects.

**Architecture:** A new `:desktop` module (Compose Multiplatform plugin, depends on `:core`) with a bespoke theme (no `MaterialTheme`), bespoke components, a sealed `LauncherState` model whose 15 visual flags drive a single declarative window, a `LauncherViewModel` that mirrors the legacy `MainController` over `StateFlow`, a state-driven second settings window, and a `renderComposeScene`-based snapshot test harness. The quarantined `legacy-javafx-ui/` is the visual/behavioral reference and is deleted at the end.

**Tech Stack:** Kotlin 2.4.0, JDK 21, Gradle 9.6.0, `org.jetbrains.compose` 1.11.1 + `org.jetbrains.kotlin.plugin.compose` 2.4.0, `com.github.gmazzo.buildconfig` 6.0.10, Sentry 8.46.0, Roborazzi 1.64.0 (snapshot regression, additive), JUnit 4.13.2.

## Global Constraints

- **Toolchain (use together):** Kotlin 2.4.0 + Gradle 9.6.0 + JDK 21 + Compose plugin 1.11.1 + compose-compiler 2.4.0. Always `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` before any `./gradlew`.
- **Bespoke theme, NOT Material.** No `MaterialTheme`. Build a `TpTheme` (CompositionLocal) + bespoke composables on `androidx.compose.foundation` primitives. **Disabled = explicit color swap, never alpha** (legacy CSS forced `opacity: 1.0`).
- **Faithful pixel-clone.** Reproduce the legacy look + behavior exactly, including the four deferred bugs and the settings save-on-invalid-heap bug (§Task 11). The only approved deviations are documented in Plan 1. The `img/Screen 1..5` mockups are the visual targets (Screen 1 = login idle w/ button; 2 = logged-in; 3 = login+button; 4 = error/pink; 5 = settings).
- **`:desktop` depends on `:core`; reuses its logic unchanged.** All login/launch/config/download logic is called through `:core` (`MinecraftContext`, `MinecraftAccountManager`, `ComposePrepare`, `ConfigHelper`, `Settings`, `MinecraftModpack`). The launch chain takes a `sk.tomsik68.mclauncher.api.ui.IProgressMonitor`.
- **Package root** `ru.lionzxy.tplauncher` (sub-packages under `desktop/src/main/kotlin/ru/lionzxy/tplauncher/`: `ui.theme`, `ui.icons`, `ui.components`, `ui.state`, `ui.window`, `ui.settings`, plus `Main.kt`).
- **Exact values:** colors, type sizes, dimensions, the 15-flag matrix, and Russian strings are embedded below (§ value tables). SVG path data and exact legacy layout live in `legacy-javafx-ui/` — implementers read those files directly rather than transcribing long path strings.
- **Snapshot strategy:** Track A (fidelity vs `img/`) = human visual sign-off + composite strips, never a CI hard-fail. Track B (regression vs our committed `golden/`) = tolerant per-pixel, CI gate. Render real composables at `density = 960f/592f` (≈1.622) → 960px-wide output to match mockups. Record/verify goldens on Linux only (Skia rendering is OS-dependent).
- **Distribution invariant:** `./gradlew fatJar` must emit exactly one self-contained cross-platform jar (all Skiko OS natives bundled) named `<name>-<version>.jar` in `build/libs/`, so `scripts/upload.sh` + `launcher.json` keep working unchanged.

---

## Value tables (authoritative — embed these; the legacy files corroborate)

### TpColors (from `:core`/legacy `Constants.kt`)
| token | hex | use |
|---|---|---|
| accent | `#00DB9D` | title, button bg, links, progress fill, check icon |
| text / second | `#FFFFFF` | primary label text, chevron fill |
| background | `#36393E` | root window bg |
| backgroundDark | `#2F3136` | progress/settings panel bg |
| backgroundCircle | `#2B2C31` | avatar circle fill |
| inputBackground | `#484C51` | field/combo/checkbox bg; disabled btn bg & track |
| backgroundProgressBar | `#DDDDDE` | progress track + default progress text |
| disable | `#7F8185` | disabled button text; disabled chevron |
| textDisable | `#AAABAD` | disabled label text; "Вход осуществлен" caption |
| error | `#D75379` | error states: title, progress text, register, apply btn |

### TpTypography
| role | face | size | color |
|---|---|---|---|
| Title | Gugi-Regular | 30sp | accent (→error) |
| Body label / field / combo | Roboto-Regular | 14sp | `#FFFFFF` |
| Button | Roboto-Regular | 16sp | `#FFFFFF` |
| "Вход осуществлен" caption | Roboto-Regular | 12sp | `#AAABAD` |

### Spacing / dimensions
DEFAULT_MARGIN `16.dp`; column gap `32.dp`; left gutter `23.dp`; title top `11.5.dp`. Window width `592.dp`; avatar `84.dp` (placeholder check `42.dp`); gear `34.dp`; checkbox `28.dp` (check `16.dp`); close-X `20.dp`; login button minHeight `36.dp`; progress bar height `12.dp` (pick 9–16, radius `5.dp`); loginComplete area minWidth `302.dp` minHeight `96.dp`. Radii: progress `5.dp`, back-button `3.5.dp` (1dp accent border), checkbox `2.5.dp`, textfield/combo/button `3.dp`.

### Icons (verbatim path data in `legacy-javafx-ui/resources/main/icon/*.svg` + the chevron inline in `legacy-javafx-ui/view/common/GlobalStylesheet.kt` arrow{} selector)
| icon | viewport | tint | where |
|---|---|---|---|
| times-solid | 352×512 | `#7F8185`-ish (themed) | close-X 20dp |
| cogs-solid | 640×512 | `#FFFFFF` | gear 34dp |
| check-solid | 512×512 | `#00DB9D` | checkbox tick 16dp / avatar placeholder 42dp |
| chevron | ~17×19 | `#FFFFFF` (disabled `#7F8185`) | combobox arrow |

### 8-state × 15-flag matrix (blank = inherits default; `BaseState` supplies the defaults and is not a rendered variant)
Defaults: titleColor=accent, loginPasswordVisible=true, successLoginVisible=false, disableProgressBar=true, disableInputField=false, progressTextColor=`#DDDDDE`, progressTextContent=null, buttonDisable=false, buttonText="Войти в игру", successLoginText="example@example.com", isOpen=true, registerFieldIsVisible=false, registerFieldColor=accent, settingsFieldIsClickable=true, disableSelectModpack=false.

| flag | Initial | ErrInitial | LoginProg | Logged | GameLoad | MCRunning | MCLaunched | ErrLaunch |
|---|---|---|---|---|---|---|---|---|
| titleColor | accent | error | | | | | | error |
| loginPasswordVisible | true | true | true | false | false | false | false | false |
| successLoginVisible | false | false | false | true | true | true | true | true |
| disableProgressBar | true | true | false | true | false | false | false | true |
| disableInputField | false | false | true | false | true | true | true | false |
| progressTextColor | `#DDDDDE` | error | | | | | | error |
| progressTextContent | "Введите логин и пароль" | err str | null | "Приятной игры" | "Загружаем игру..." | (inh) | (inh) | err str |
| buttonDisable | false | true | true | false | true | true | true | false |
| buttonText | "Войти в игру" | err str | "Войти в игру" | "Войти в игру" | (inh) | (inh) | (inh) | "Войти в игру" |
| successLoginText | example@ | example@ | example@ | email | email | email | email | email |
| isOpen | true | true | true | true | true | true | **false→quit** | true |
| registerFieldIsVisible | true | true | false | false | false | false | false | false |
| registerFieldColor | accent | error | | | | | | accent |
| settingsFieldIsClickable | true | true | true | true | true | false | false | true |
| disableSelectModpack | false | false | true | false | true | true | true | false |

A `null` `progressTextContent` must NOT clear the progress label (the ViewModel's imperative `setStatus(...)` survives). `isOpen=false` quits the app; `settingsFieldIsClickable=false` disables the gear. Combo display values: `Vanilla` / `NewHorizon` / `Nomifactory` (`MinecraftModpack.modpackName`).

### Russian strings (single `Strings` object; preserve verbatim incl. the typo "Внутреняя")
`games.glitchless.ru`, `Логин`(label shown in mockups; legacy code uses "Email" — **use "Логин"/"Пароль" to match the mockups**), `Пароль`, `Сервер`, `Настройки запуска`, `Регистрация на сайте`, `Войти в игру`, `Вход осуществлен`, `Введите логин и пароль`, `Приятной игры`, `Загружаем игру...`, `Авторизация по email %s...`, `Запускаем Minecraft...`, `Введите валидную почту`, `Пароль не может быть пустым`, `Проверьте подключение к интернету`, `Внутреняя ошибка, мы уже исправляем это`, `Объем памяти`, `Параметры java`, `Prefix`, `Путь до Java`, `Дебаг-режим`, `Авто-заход на сервер`, `Перейти в директорию игры`, `Выйти из аккаунта`, `Очистить папку с бекапом (%s)`, `Удалить игру и сбросить настройки лаунчера`, `Вернуться`, `Применить`. Error-state button/progress text examples seen in mockups: `Введите правильные логин и пароль` / `Неправильный логин или пароль` (these come from the ViewModel error messages; map the controller's messages to these where they differ — confirm against `MainController` messages).

---

## Task 1: `:desktop` module + Compose toolchain de-risk (minimal app)

**Files:**
- Modify: `settings.gradle.kts` (add `include(":desktop")`)
- Modify: `gradle/libs.versions.toml` (add roborazzi version/lib/plugin; compose plugins already present)
- Create: `desktop/build.gradle.kts`
- Create: `desktop/src/main/kotlin/ru/lionzxy/tplauncher/Main.kt` (minimal `application {}`)

**Interfaces:**
- Produces: a runnable `:desktop` Compose app on the new toolchain.

- [ ] **Step 1: Add Roborazzi to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add `roborazzi = "1.64.0"`; under `[libraries]` add:
```toml
roborazzi-compose-desktop = { module = "io.github.takahirom.roborazzi:roborazzi-compose-desktop", version.ref = "roborazzi" }
```
under `[plugins]` add:
```toml
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 2: Add `:desktop` to settings**

In `settings.gradle.kts`, add after `include(":core")`:
```kotlin
include(":desktop")
```
Ensure `dependencyResolutionManagement.repositories` includes `google()` and the JetBrains Compose dev repo (needed by the compose plugin artifacts):
```kotlin
maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
google()
```
(Add only if missing; `mavenCentral()` + jitpack are already there.)

- [ ] **Step 3: Write `desktop/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.roborazzi)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)   // foundation + ui (ImageComposeScene) + Skiko
    implementation(libs.sentry)
    testImplementation(libs.junit)
    testImplementation(libs.roborazzi.compose.desktop)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
}

compose.desktop {
    application {
        mainClass = "ru.lionzxy.tplauncher.MainKt"
    }
}
```
(BuildConfig + fatJar config are added in Tasks 11–12; keep this minimal for now. `compose.desktop` block requires `import org.jetbrains.compose.desktop.application.dsl.TargetFormat` only when using nativeDistributions — not yet.)

- [ ] **Step 4: Minimal `Main.kt`**

Create `desktop/src/main/kotlin/ru/lionzxy/tplauncher/Main.kt`:
```kotlin
package ru.lionzxy.tplauncher

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// NOTE: compose.desktop.currentOs bundles Material 2 + foundation + ui, NOT material3.
// Use foundation's BasicText (matches the bespoke no-Material mandate). Do not add material3.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TechnoparkLauncher") {
        BasicText("TechnoparkLauncher :desktop bring-up")
    }
}
```

- [ ] **Step 5: De-risk the Compose toolchain**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :desktop:compileKotlin
```
Expected: `BUILD SUCCESSFUL` (the Compose compiler plugin applies on Kotlin 2.4, `compose.desktop.currentOs` resolves Skiko for Linux). If the compose plugin can't resolve, confirm Step 2's repos. Do NOT run `:desktop:run` in headless CI (no display); compile is the gate here.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "build(desktop): add :desktop Compose module (toolchain bring-up)"
```
(End commit body with the `Claude-Session:` trailer — applies to every commit in this plan.)

---

## Task 2: Snapshot harness (`renderComposeScene` → PNG, headless, custom fonts)

**Files:**
- Create: `desktop/src/test/resources/fonts/Gugi-Regular.ttf`, `Roboto-Regular.ttf` (copied)
- Create: `desktop/src/test/kotlin/ru/lionzxy/tplauncher/snapshot/SnapshotHarness.kt`
- Create: `desktop/src/test/kotlin/ru/lionzxy/tplauncher/snapshot/HarnessSmokeTest.kt`

**Interfaces:**
- Produces: `fun snapshot(name: String, widthPx: Int, heightPx: Int, density: Float = 960f/592f, content: @Composable () -> Unit): File` writing `build/snapshots/<name>.png`; `val SnapGugi`, `val SnapRoboto` FontFamilies for tests.

- [ ] **Step 1: Copy the fonts into the test module**

```bash
mkdir -p desktop/src/test/resources/fonts
cp legacy-javafx-ui/resources/main/Gugi-Regular.ttf  desktop/src/test/resources/fonts/
cp legacy-javafx-ui/resources/main/Roboto-Regular.ttf desktop/src/test/resources/fonts/
```
(Same-module test resources avoid the cross-JAR `Font(resource:)` bug.)

- [ ] **Step 2: Write the harness**

Create `desktop/src/test/kotlin/ru/lionzxy/tplauncher/snapshot/SnapshotHarness.kt`:
```kotlin
package ru.lionzxy.tplauncher.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

val SnapGugi = FontFamily(Font("fonts/Gugi-Regular.ttf", FontWeight.Normal))
val SnapRoboto = FontFamily(Font("fonts/Roboto-Regular.ttf", FontWeight.Normal))

fun headlessInit() {
    System.setProperty("java.awt.headless", "true")
    System.setProperty("skiko.renderApi", "SOFTWARE")
}

/** Renders [content] to build/snapshots/<name>.png at the given pixel size; returns the file. */
fun snapshot(
    name: String,
    widthPx: Int,
    heightPx: Int,
    density: Float = 960f / 592f,   // 592dp window -> 960px to match img/ mockups
    content: @Composable () -> Unit,
): File {
    headlessInit()
    val image = renderComposeScene(widthPx, heightPx, Density(density), content = content)
    val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encode null for $name")
    val out = File("build/snapshots/$name.png").apply { parentFile.mkdirs() }
    out.writeBytes(data.bytes)
    return out
}
```

- [ ] **Step 3: Smoke test (custom font + headless render)**

Create `HarnessSmokeTest.kt`:
```kotlin
package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessSmokeTest {
    @Test fun rendersWithCustomFont() {
        val f = snapshot("smoke", 960, 200) {
            BasicText(
                "games.glitchless.ru",
                modifier = Modifier.fillMaxSize().background(Color(0xFF36393E)),
                style = TextStyle(color = Color(0xFF00DB9D), fontFamily = SnapGugi),
            )
        }
        assertTrue("empty png", f.length() > 0)
    }
}
```

- [ ] **Step 4: Run**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :desktop:test --tests "ru.lionzxy.tplauncher.snapshot.HarnessSmokeTest"
```
Expected: `BUILD SUCCESSFUL`, `desktop/build/snapshots/smoke.png` exists and is non-empty. If Skiko complains about fontconfig on a minimal box: `sudo apt-get install -y fontconfig`. If `renderComposeScene` needs an opt-in, add `@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "test(desktop): headless renderComposeScene snapshot harness + fonts"
```

---

## Task 3: Theme — `TpColors`, `TpTypography`, `TpDimens`, `TpTheme`

**Files:**
- Create: `desktop/src/main/resources/fonts/{Gugi,Roboto}-Regular.ttf` (bundle for the app)
- Create: `desktop/.../ui/theme/TpTheme.kt`

**Interfaces:**
- Produces: `object TpColors { val accent, text, background, backgroundDark, backgroundCircle, input, progressTrack, disable, textDisable, error: Color }`; `TpTypography` (title/body/button/caption `TextStyle`s using bundled fonts); `TpDimens` (the dp constants from the value table); `@Composable fun TpTheme(content: @Composable () -> Unit)` providing them via CompositionLocals (`LocalTpColors`, `LocalTpTypography`, `LocalTpDimens`).

- [ ] **Step 1: Bundle the app fonts**

```bash
mkdir -p desktop/src/main/resources/fonts
cp legacy-javafx-ui/resources/main/Gugi-Regular.ttf  desktop/src/main/resources/fonts/
cp legacy-javafx-ui/resources/main/Roboto-Regular.ttf desktop/src/main/resources/fonts/
```

- [ ] **Step 2: Write `TpTheme.kt`** with the exact tokens (full code)

```kotlin
package ru.lionzxy.tplauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TpColors {
    val accent = Color(0xFF00DB9D)
    val text = Color(0xFFFFFFFF)
    val background = Color(0xFF36393E)
    val backgroundDark = Color(0xFF2F3136)
    val backgroundCircle = Color(0xFF2B2C31)
    val input = Color(0xFF484C51)
    val progressTrack = Color(0xFFDDDDDE)
    val disable = Color(0xFF7F8185)
    val textDisable = Color(0xFFAAABAD)
    val error = Color(0xFFD75379)
}

private val Gugi = FontFamily(Font("fonts/Gugi-Regular.ttf", FontWeight.Normal))
private val Roboto = FontFamily(Font("fonts/Roboto-Regular.ttf", FontWeight.Normal))

object TpTypography {
    val title = TextStyle(fontFamily = Gugi, fontSize = 30.sp, color = TpColors.accent)
    val body = TextStyle(fontFamily = Roboto, fontSize = 14.sp, color = TpColors.text)
    val button = TextStyle(fontFamily = Roboto, fontSize = 16.sp, color = TpColors.text)
    val caption = TextStyle(fontFamily = Roboto, fontSize = 12.sp, color = TpColors.textDisable)
}

object TpDimens {
    val margin = 16.dp; val columnGap = 32.dp; val gutter = 23.dp; val titleTop = 11.5.dp
    val windowWidth = 592.dp; val avatar = 84.dp; val avatarCheck = 42.dp
    val gear = 34.dp; val checkbox = 28.dp; val checkboxTick = 16.dp; val closeX = 20.dp
    val buttonMinHeight = 36.dp; val progressHeight = 12.dp; val progressRadius = 5.dp
    val fieldRadius = 3.dp; val checkboxRadius = 2.5.dp; val backButtonRadius = 3.5.dp
}

val LocalTpColors = staticCompositionLocalOf { TpColors }
val LocalTpTypography = staticCompositionLocalOf { TpTypography }
val LocalTpDimens = staticCompositionLocalOf { TpDimens }

@Composable
fun TpTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTpColors provides TpColors,
        LocalTpTypography provides TpTypography,
        LocalTpDimens provides TpDimens,
        content = content,
    )
}
```

- [ ] **Step 3: Theme snapshot test** — render swatches + the three type roles, assert non-empty (visual review later). Add `ThemeSnapshotTest.kt` rendering a Column of the title/body/button styles on the bg; gate `./gradlew :desktop:test --tests "*ThemeSnapshotTest"` green + PNG written.

- [ ] **Step 4: Commit** `feat(desktop): bespoke TpTheme (colors, type, dimens)`

---

## Task 4: Icons — `ImageVector`s from the verbatim SVG paths

**Files:**
- Create: `desktop/.../ui/icons/TpIcons.kt`

**Interfaces:**
- Produces: `object TpIcons { val Times: ImageVector; val Cogs: ImageVector; val Check: ImageVector; val Chevron: ImageVector }` with the correct viewports (352×512, 640×512, 512×512, 17×19).

- [ ] **Step 1: Read the four path strings** from `legacy-javafx-ui/resources/main/icon/{times,cogs,check}-solid.svg` (the `d="..."` attribute) and the chevron from `legacy-javafx-ui/view/common/GlobalStylesheet.kt` (the `arrow {}` selector's `-fx-shape` path). Copy each `d` verbatim.

- [ ] **Step 2: Build each `ImageVector`** via `ImageVector.Builder(defaultWidth=…dp, defaultHeight=…dp, viewportWidth=…f, viewportHeight=…f).addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White))…build()`. Tint at call sites (don't bake fill). (Full skeleton for one icon, repeat for the others with their viewports.)

```kotlin
package ru.lionzxy.tplauncher.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun svg(name: String, vw: Float, vh: Float, d: String): ImageVector =
    ImageVector.Builder(name, vw.dp, vh.dp, vw, vh).also { b ->
        b.addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White))
    }.build()

object TpIcons {
    val Times = svg("times", 352f, 512f, "M242.72 256l100.07-100.07c12.28-12.28 …")   // paste verbatim
    val Cogs = svg("cogs", 640f, 512f, "M512.1 191l-8.2 14.3c-3 5.3 …")                 // paste verbatim
    val Check = svg("check", 512f, 512f, "M173.898 439.404l-166.4-166.4c-9.997 …")      // paste verbatim
    val Chevron = svg("chevron", 17f, 19f, "M7.85598 14.1563L0.481353 6.94431c …")      // paste verbatim
}
```

- [ ] **Step 3: Snapshot** the four icons tinted (close `#7F8185`, gear white, check accent, chevron white) on the bg; gate `:desktop:test --tests "*IconSnapshotTest"` green; visually confirm shapes render (not blank). 
- [ ] **Step 4: Commit** `feat(desktop): FontAwesome + chevron ImageVectors`

---

## Task 5: Strings + base components — `Strings`, `TpButton`, `TpTextField`, `TpServerCombo`

**Files:** `desktop/.../ui/Strings.kt`, `desktop/.../ui/components/TpButton.kt`, `TpTextField.kt`, `TpServerCombo.kt`

**Interfaces:**
- Produces: `object Strings { … }` (every literal from the value table); `@Composable fun TpButton(text, enabled, onClick)`; `@Composable fun TpTextField(value, onValueChange, label, password=false, enabled=true)`; `@Composable fun TpServerCombo(items: List<String>, selectedIndex, onSelect, enabled)`.

- [ ] **Step 1: `Strings.kt`** — one `const val`/`val` per literal in the value table (verbatim Russian, incl. "Внутреняя"); template ones as functions: `fun authByEmail(email: String) = "Авторизация по email $email..."`, `fun clearBackup(size: String) = "Очистить папку с бекапом ($size)"`.
- [ ] **Step 2: `TpButton`** — `Box(Modifier.fillMaxWidth().heightIn(min=36.dp).clip(RoundedCornerShape(3.dp)).background(if(enabled) accent else input).clickable(enabled){onClick()}.pointerHoverIcon(Hand), center)` with `BasicText(text, style=button.copy(color = if(enabled) text else disable))`. Disabled = color swap, not alpha.
- [ ] **Step 3: `TpTextField`** — labeled field: `Column { BasicText(label, body); Spacer; Box(bg=input, radius 3.dp, padding).BasicTextField(value, onValueChange, enabled, textStyle=body, visualTransformation = if(password) PasswordVisualTransformation() else None, readOnly = !enabled, cursorBrush=accent) }`. Disabled text color `#AAABAD`. Fire `onValueChange` (the screen wires it to `vm.onPasswordOrLoginChange()`).
- [ ] **Step 4: `TpServerCombo`** — `Box(bg=input, radius 3.dp){ Row{ BasicText(items[selectedIndex], body); Spacer(weight); Icon(TpIcons.Chevron, tint = if(enabled) text else disable) } }` opening a dropdown (`DropdownMenu` from foundation/ — or a bespoke popup) listing items (bg input, white text); `onSelect(index)`.
- [ ] **Step 5: Snapshot** a sample row (button + field + combo) → gate green + PNG; visual check vs the field styling in Screen 1.
- [ ] **Step 6: Commit** `feat(desktop): Strings + TpButton/TpTextField/TpServerCombo`

---

## Task 6: Remaining components — `TpCheckBox`, `Avatar`, `TpProgressBar`, `ProgressPanel`, chrome bits

**Files:** `desktop/.../ui/components/{TpCheckBox,Avatar,TpProgressBar,ProgressPanel,Chrome}.kt`; `desktop/.../ui/AvatarLoader.kt`; `desktop/.../data/AvatarResponse.kt`

**Interfaces:**
- Produces: `@Composable fun TpCheckBox(checked, onChange)`; `@Composable fun Avatar(modifier)`; `@Composable fun TpProgressBar(value: Float, enabled: Boolean)` (`value==-1f` → indeterminate); `@Composable fun ProgressPanel(text, textColor, progress: ProgressUiState, enabled)`; `CloseX`, `GearRow`, `RegisterLink`, `Title` composables; `suspend fun loadAvatar(login: String): ImageBitmap?`.

- [ ] **Step 1: `AvatarResponse` (Gson, replaces json-smart)**
```kotlin
package ru.lionzxy.tplauncher.data
import com.google.gson.annotations.SerializedName
data class AvatarResponse(val data: AvatarData)
data class AvatarData(@SerializedName("avatar_url") val avatarUrl: String)
```
- [ ] **Step 2: `AvatarLoader`** — `suspend` fn: read `<defaultDir>/avatar.png` cache (emit if present); GET `https://games.glitchless.ru/api/minecraft/users/profiles/$login/avatar/`, parse with Gson → `avatar_url`, GET image bytes → write `avatar.png` → decode to `ImageBitmap`. Serialize with a `Mutex`. (Use `:core`'s `ConfigHelper.getDefaultDirectory()` and the existing UA-configured HTTP.)
- [ ] **Step 3: `TpCheckBox`** — `Box(28.dp, bg=input, radius 2.5.dp).clickable{onChange(!checked)}` with `if(checked) Icon(TpIcons.Check, 16.dp, tint=accent)` centered. Derive visibility purely from `checked` (no init flash).
- [ ] **Step 4: `Avatar`** — `Box(84.dp.clip(CircleShape), center)`: placeholder = `background(backgroundCircle)` + `Icon(TpIcons.Check, 42.dp, tint=accent)`; loaded = `Image(bitmap, contentScale=Crop, Modifier.size(84.dp).clip(CircleShape))`. `Avatar` reads the nickname from `ConfigHelper.config.profile?.login` **internally** (the player login, NOT the typed email) — if null, stay on the placeholder and do not fetch. Collect `loadAvatar(login)` via `produceState`.
- [ ] **Step 5: `TpProgressBar`** — track `RoundedCornerShape(5.dp)` height 12.dp bg=`if(enabled) progressTrack else input`; determinate fill `Box(fillMaxWidth(value.coerceIn(0f,1f))).background(accent)`; `value==-1f` → indeterminate animated bar (InfiniteTransition sweeping an accent segment). Disabled → fill 0, track `#484C51`.
- [ ] **Step 6: `ProgressPanel`** — `Column(bg=backgroundDark, padding 16.dp, spacing 16.dp, centered){ BasicText(text, body.copy(color=textColor)); TpProgressBar(value, enabled) }`.
- [ ] **Step 7: Chrome** — `CloseX` (TpIcons.Times 20.dp, clickable, hand), `Title` (Strings.title, TpTypography.title, color param), `RegisterLink` (underlined accent/error, clickable→browser), `GearRow` (TpIcons.Cogs 34.dp + Strings.settings body, clickable when enabled).
- [ ] **Step 8: Snapshot** each (checkbox checked/unchecked, avatar placeholder, progress determinate+indeterminate, panel) → gate green + PNGs.
- [ ] **Step 9: Commit** `feat(desktop): checkbox, avatar (Gson loader), progress, chrome components`

---

## Task 7: State model — sealed `LauncherState` + 15-flag derivation + `ProgressUiState`

**Files:** `desktop/.../ui/state/LauncherState.kt`, `ProgressUiState.kt`; test `LauncherStateTest.kt`

**Interfaces:**
- Produces: `sealed class LauncherState` variants (`Initial`, `InitialError(error)`, `LoginProgress`, `Logged(email)`, `GameLoading(email)`, `MinecraftRunning(email)`, `MinecraftLaunched(email)`, `LaunchError(email, error)`); `data class StateFlags(15 fields)`; `val LauncherState.flags: StateFlags`; `data class ProgressUiState(status: String? = null, value: Float = 0f)`.

- [ ] **Step 1: Write the failing test** — `LauncherStateTest` asserting the matrix, e.g.:
```kotlin
@Test fun initialErrorFlags() {
    val f = LauncherState.InitialError("боом").flags
    assertEquals(TpColors.error, f.titleColor)
    assertTrue(f.buttonDisable); assertEquals("боом", f.buttonText)
    assertEquals(TpColors.error, f.registerFieldColor)
}
@Test fun mcLaunchedQuits() { assertFalse(LauncherState.MinecraftLaunched("a@b").flags.isOpen) }
@Test fun loggedShowsSuccessAndEmail() {
    val f = LauncherState.Logged("a@b").flags
    assertTrue(f.successLoginVisible); assertFalse(f.loginPasswordVisible)
    assertEquals("a@b", f.successLoginText); assertEquals("Приятной игры", f.progressTextContent)
}
```
- [ ] **Step 2: Run → fails** (`./gradlew :desktop:test --tests "*LauncherStateTest"`).
- [ ] **Step 3: Implement** `StateFlags` (defaults from the value table) + `LauncherState` sealed class + `val LauncherState.flags` `when`-expression reproducing the 9×15 matrix exactly. `ProgressUiState` as specified.
- [ ] **Step 4: Run → passes.**
- [ ] **Step 5: Commit** `feat(desktop): LauncherState model + 15-flag matrix`

---

## Task 8: `LauncherViewModel` + `ProgressMonitorBridge`

**Files:** `desktop/.../ui/LauncherViewModel.kt`, `ProgressMonitorBridge.kt`; test `LauncherViewModelTest.kt`

**Interfaces:**
- Consumes: `:core` `MinecraftContext`, `MinecraftAccountManager`, `ComposePrepare`, `ConfigHelper`, `MinecraftModpack`, `LogoUtils.setLogoForMinecraft`, `IProgressMonitor`.
- Produces: `class LauncherViewModel(scope: CoroutineScope)` with `val state: StateFlow<LauncherState>`, `val progress: StateFlow<ProgressUiState>`, `fun onInitView()`, `onButtonClick(email, password)`, `onChangeModpack(pack)`, `onPasswordOrLoginChange()`.

- [ ] **Step 1: `ProgressMonitorBridge`** — adapts `:core`'s `sk.tomsik68.mclauncher.api.ui.IProgressMonitor` (4 methods: `setProgress(Int)`, `setMax(Int)`, `incrementProgress(Int)`, `setStatus(String?)`) onto the progress `StateFlow`. Full code:
```kotlin
package ru.lionzxy.tplauncher.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor

class ProgressMonitorBridge(private val flow: MutableStateFlow<ProgressUiState>) : IProgressMonitor {
    private var max = 1
    private var cur = 0
    override fun setMax(len: Int) { max = if (len <= 0) 1 else len }
    override fun setProgress(progress: Int) { cur = progress; emit() }
    override fun incrementProgress(amount: Int) { setProgress(cur + amount) }
    override fun setStatus(status: String?) { if (status != null) flow.update { it.copy(status = status) } } // null = no-op
    private fun emit() = flow.update { it.copy(value = if (cur == -1) -1f else cur.toFloat() / max) }
}
```
- [ ] **Step 2: Failing tests** — with a fake account manager / injected seams: `onInitView` logged→`Logged`, else `Initial`; `onLogin` invalid email→`InitialError("Введите валидную почту")`; empty password→`InitialError("Пароль не может быть пустым")`; `onPasswordOrLoginChange` resets `InitialError`→`Initial`. (Mirror `MainController.kt`; reuse its messages.)
- [ ] **Step 3: Run → fails.**
- [ ] **Step 4: Implement** `LauncherViewModel` by porting the transitions in `legacy-javafx-ui/view/main/MainController.kt` (read it — `onInitView`/`onButtonClick`/`onLogin`/`onGameStart`/`onChangeModpack`/`onPasswordOrLoginChange`), running login/launch on `Dispatchers.IO`, emitting state via `StateFlow`, bridging progress through `ProgressMonitorBridge`. **Reuse MainController's exact error strings verbatim** ("Введите валидную почту", "Пароль не может быть пустым", "Проверьте подключение к интернету", "Внутреняя ошибка, мы уже исправляем это"). Keep the faithful `delay(60_000)`→`MinecraftLaunched` and the `UnknownHostException`→`InitialError` routing.
- [ ] **Step 5: Run → passes.**
- [ ] **Step 6: Commit** `feat(desktop): LauncherViewModel + progress bridge`

---

## Task 9: `MainWindow` composable + 4-state snapshot fidelity

**Files:** `desktop/.../ui/window/MainWindow.kt`; test `MainWindowSnapshotTest.kt`

**Interfaces:**
- Consumes: theme, components, `LauncherState.flags`, `ProgressUiState`.
- Produces: `@Composable fun MainWindowContent(state: LauncherState, progress: ProgressUiState, callbacks: MainCallbacks)` rendering the layout tree (§6) — the 592dp content used both in the real window and in snapshots.

- [ ] **Step 1: Implement `MainWindowContent`** with this layout tree (self-contained; corroborate spacing against `legacy-javafx-ui/view/main/MainWindow.kt`): root `Box(Modifier.fillMaxWidth())` — **`fillMaxWidth`, NOT `widthIn(max=592.dp)`**: the 592dp cap comes from the window width, and `fillMaxWidth` makes the content fill the render scene so snapshots aren't left-aligned in transparent dead space — → `Column{ Title; Form(grid 2×2 + overlaid loginComplete block toggled by successLoginVisible/loginPasswordVisible); RegisterLink(if registerFieldIsVisible); TpButton; ProgressPanel } + CloseX(TopEnd)`. Drive every widget from `state.flags` (visibility, colors, disabled, button text) + `progress`. (Drag/window wiring is Task 11; this is pure content.)
- [ ] **Step 2: Snapshot the 4 main states** (deterministic preview state, fonts from test resources, density 960/592):
```kotlin
// Four DISTINCT states, each chosen so its 15 flags match the mockup's visible layout:
@Test fun screen1_login()      = snap("login",      612, LauncherState.Initial)                       // idle form + register + button + default progress text
@Test fun screen2_loggedIn()   = snap("loggedIn",   540, LauncherState.Logged("st3althtech@mail.ru"))  // avatar + "Вход осуществлен"
@Test fun screen3_loginProg()  = snap("loginProg",  540, LauncherState.LoginProgress)                  // form visible, inputs+combo disabled, active progress bar
@Test fun screen4_error()      = snap("error",      540, LauncherState.InitialError("Введите валидную почту")) // a REAL MainController error string (not the mockup's placeholder)
```
(`snap` wraps `snapshot(name, 960, h){ TpTheme { MainWindowContent(state, fixedProgress, noopCallbacks) } }`.) Gate: `:desktop:test --tests "*MainWindowSnapshotTest"` green + 4 PNGs in `build/snapshots/`.
- [ ] **Step 3: Fidelity check (Track A)** — compare each `build/snapshots/*.png` to the matching `img/Screen N.{png,jpg}` visually (the controller/human reviews; see Task 13 for composite strips). Adjust component spacing/sizes until faithful. This iterative visual match is the real gate for the clone.
- [ ] **Step 4: Commit** `feat(desktop): MainWindow content + main-state snapshots`

---

## Task 10: `SettingsWindow` composable + settings VM + the save-on-invalid bug

**Files:** `desktop/.../ui/settings/{SettingsWindow,SettingsViewModel}.kt`; tests `SettingsViewModelTest.kt`, `SettingsSnapshotTest.kt`

**Interfaces:**
- Consumes: `:core` `Settings`, `ConfigHelper`, `SystemMemoryHelper`, the action behaviors.
- Produces: `@Composable fun SettingsWindowContent(vm: SettingsViewModel)`; `class SettingsViewModel` with fields (heap, javaArgs, prefix, javaPath, debug, autoJoin), `fun apply()`, `fun back()`, the 4 actions, and `heapError: StateFlow<String?>`.

- [ ] **Step 1: Failing test for the heap save-bug** — `apply()` with invalid heap (e.g. "3GB") sets `heapError != null` AND still persists the other fields AND signals close (replicate exactly: validate, set error, but do NOT return — fall through to save+close). Valid heap ("3G") persists heap too. (Heap regex full-match `[0-9]*[G|g|M|m]` per spec.)
- [ ] **Step 2: Run → fails.**
- [ ] **Step 3: Implement `SettingsViewModel`** loading from `Settings(ConfigHelper.config.settings)`; the 4 actions (open game dir via `Desktop.browse`/OS open, logout=clear profile+exit, clear backup=delete `technomine/backup`+refresh size, wipe=delete default dir except `jrepath.txt`/`jre/`+exit) calling `:core`; the live RAM-field listener clearing the error. `SettingsWindowContent` layout (corroborate against `legacy-javafx-ui/view/settings/SettingsWindow.kt`): the title row + a close-X (reuse `CloseX` from Task 6, top-right 20dp, wired in Task 11 to close the settings window), 6 controls (4 fields + 2 checkboxes), 4 action labels, and the back/apply button row — exact Russian labels; the title doubles as the heap-error indicator (turns `#D75379`).
- [ ] **Step 4: Run → passes.**
- [ ] **Step 5: Settings snapshot** — `snapshot("settings", 960, 776){ TpTheme { SettingsWindowContent(previewVm) } }`; gate green + PNG; fidelity vs `img/Screen 5`.
- [ ] **Step 6: Commit** `feat(desktop): settings window + faithful heap save-bug`

---

## Task 11: App entry — `application{}`, window chrome, drag, multi-window, Sentry, BuildConfig

**Files:** `desktop/build.gradle.kts` (BuildConfig block), `desktop/.../Main.kt` (real), `desktop/.../ui/window/AppWindows.kt`

**Interfaces:**
- Produces: the real `fun main()` wiring `LauncherViewModel` + windows.

- [ ] **Step 1: BuildConfig** — add to `desktop/build.gradle.kts`:
```kotlin
buildConfig {
    packageName("ru.lionzxy.tplauncher")
    buildConfigField("String", "NAME", "\"TechnoparkLauncher\"")
    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "SENTRY_DSN", "\"https://cd312e191fbd44b49c6cc526bb91817c@sentry.team.glitchless.ru/18\"")
}
```
- [ ] **Step 2: Real `Main.kt`** (bootstrap order — UA first):
```kotlin
fun main() {
    configureHttpUserAgent()                              // 1. FIRST (from :core)
    Sentry.init { it.dsn = BuildConfig.SENTRY_DSN; it.serverName = BuildConfig.NAME
                  it.release = BuildConfig.VERSION; it.setTag("version", BuildConfig.VERSION) }  // 2
    LogoUtils.prepareLogo()                               // :core
    application {                                         // 3
        val scope = rememberCoroutineScope()
        val vm = remember { LauncherViewModel(scope) }
        LaunchedEffect(Unit) { vm.onInitView() }
        val state by vm.state.collectAsState(); val progress by vm.progress.collectAsState()
        if (!state.flags.isOpen) exitApplication()        // MCLaunched quits
        Window(onCloseRequest = ::exitApplication, undecorated = true, resizable = false,
               state = rememberWindowState(width = 592.dp, height = Dp.Unspecified),
               icon = painterResource("icon/logo.png"), title = "TechnoparkLauncher") {
            WindowDraggableArea { /* background/title drag layer */ }
            TpTheme { MainWindowContent(state, progress, callbacks(vm, openSettings = { showSettings = true })) }
        }
        if (showSettings) Window(onCloseRequest = { showSettings = false }, undecorated = true, …) {
            TpTheme { SettingsWindowContent(settingsVm) }
        }
    }
}
```
- [ ] **Step 3: Drag scoping** — wrap only title/background dead-space in `WindowDraggableArea`; fields/combo/button/close-X/gear/register must sit outside it or consume pointer events (§6).
- [ ] **Step 4: Wire controls** — close→exit; register/title→`Desktop.browse`; gear→`showSettings=true` (disabled when `!settingsFieldIsClickable`); button→`vm.onButtonClick(email, password)`; field edits→`vm.onPasswordOrLoginChange`. Combo (index↔enum by declaration order):
```kotlin
TpServerCombo(
    items = MinecraftModpack.values().map { it.modpackName },
    selectedIndex = MinecraftModpack.values().indexOf(ConfigHelper.config.currentModpack),
    onSelect = { i -> vm.onChangeModpack(MinecraftModpack.values()[i]) },
    enabled = !state.flags.disableSelectModpack,
)
```
- [ ] **Step 5: Move desktop halves of `LogoUtils`/`ResourceHelper`** if still needed (window icon via `painterResource`); ensure `icon/logo.png` resolves from `:core` resources on the classpath.
- [ ] **Step 6: Run the real app**
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
GDK_BACKEND= ./gradlew :desktop:run    # on a machine with a display
```
Expected: undecorated 592dp window, draggable, login form matches Screen 1; gear opens settings; close quits. (Headless CI: gate on `:desktop:compileKotlin` + the snapshots instead.)
- [ ] **Step 7: Commit** `feat(desktop): app entry, window chrome, drag, settings window, Sentry init`

---

## Task 12: Distribution — cross-platform fat jar (Skiko natives) keeping `upload.sh` compatible

**Files:** `desktop/build.gradle.kts` (fatJar task + all-OS Skiko deps)

- [ ] **Step 1: Bundle all Skiko OS natives** so one jar runs everywhere. Compose 1.11.1 resolves Skiko **0.144.6** (verify with `./gradlew :desktop:dependencies | grep skiko`; bump the version below if it differs). Add all five per-OS runtimes as `runtimeOnly`:
```kotlin
val skiko = "0.144.6"
runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skiko")
runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:$skiko")
runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skiko")
runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skiko")
runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skiko")
```
- [ ] **Step 2: Register `fatJar`** in `:desktop` producing exactly one `build/libs/<name>-<version>.jar`, bundling `:core` + `:desktop` + all deps + all 5 Skiko natives. Do NOT use `packageUberJarForCurrentOS` (it bundles only the current OS's Skiko → single-platform). Use a `Jar` task with duplicate-merge (the 5 skiko jars share `META-INF` service entries):
```kotlin
tasks.register<Jar>("fatJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "ru.lionzxy.tplauncher.MainKt" }
    archiveBaseName.set("TechnoparkLauncher")   // -> TechnoparkLauncher-<version>.jar
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}
```
Clean stale jars first (`build/libs` must hold exactly one jar so `scripts/upload.sh`'s `<name>-<version>.jar` pattern matches).
- [ ] **Step 3: Verify** `./gradlew fatJar` → one jar; `java -jar build/libs/*.jar` launches (on a display); confirm `scripts/upload.sh`'s `JARPATH`/`VERSION` parsing still matches the name pattern.
- [ ] **Step 4: Commit** `build(desktop): cross-platform Skiko uber jar (upload.sh-compatible)`

---

## Task 13: Snapshot regression baselines + fidelity composites

**Files:** `desktop/src/test/resources/golden/*.png` (committed), `desktop/.../snapshot/FidelityComposite.kt`, Roborazzi config

- [ ] **Step 1: Roborazzi smoke (go/no-go gate)** — `captureRoboImage` has NO `skia.Image`/composable overload on desktop; it only extends `ImageBitmap`. Bridge the harness output (`renderComposeScene` returns `org.jetbrains.skia.Image`) via `.toComposeImageBitmap()`:
```kotlin
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.takahirom.roborazzi.RoborazziOptions
import com.dropbox.differ.SimpleImageComparator   // NOTE: SimpleImageComparator lives in com.dropbox.differ (transitive), NOT roborazzi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import java.io.File

val opts = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(
    changeThreshold = 0.001f,
    imageComparator = SimpleImageComparator(maxDistance = 0.007f, hShift = 1, vShift = 1),
))
renderComposeScene(960, 612, Density(960f / 592f)) { TpTheme { MainWindowContent(/* Initial */) } }
    .toComposeImageBitmap()
    .captureRoboImage(file = File("src/test/resources/golden/login.png"), roborazziOptions = opts)
```
Run `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 && ./gradlew :desktop:recordRoborazziJvm`. **If Roborazzi fights the Kotlin-2.4/CMP-1.11.1/Gradle-9.6 stack** (it's built upstream against older versions), fall back to the dependency-free `renderComposeScene` + `javax.imageio` per-pixel diff (~40 lines: decode golden + actual, compare with a tolerance) and drop the roborazzi plugin/dep. **Note which path you took in the report.** (Task names use the `Jvm` suffix because `:desktop` is a plain `kotlin-jvm` module; if a task isn't found, run `./gradlew :desktop:tasks --all | grep -i roborazzi`.)
- [ ] **Step 2: Record goldens** for all 5 states into `src/test/resources/golden/`, commit them (record on **Linux** only — Skia rendering is OS-dependent). Reuse the `opts`/comparator from Step 1 (`com.dropbox.differ.SimpleImageComparator(maxDistance=0.007f, hShift=1, vShift=1)`, `changeThreshold=0.001f`).
- [ ] **Step 3: Regression gate** — `./gradlew :desktop:verifyRoborazziJvm` green against the committed goldens.
- [ ] **Step 4: Fidelity composites (Track A, advisory)** — a test that writes `[mockup | ours | abs-diff]` strips to `build/fidelity/<name>_composite.png` for the 5 states (read `img/Screen*.{png,jpg}`, scale to match, side-by-side). NOT a CI hard-fail.
- [ ] **Step 5: Human fidelity sign-off** — the controller reads each composite + the raw `build/snapshots/*` vs `img/Screen*` and confirms the clone is faithful (or files targeted spacing/color fixes back into Tasks 9/10). **Ignore mockup-only placeholder text** ("Альфа Центавра", "Попытка авторизации…", "Приветики") — those are non-production; the authoritative content is `MinecraftModpack.modpackName` (Vanilla/NewHorizon/Nomifactory) + the state matrix's progress strings. Judge layout, palette, typography, spacing — not literal text.
- [ ] **Step 6: Commit** `test(desktop): committed snapshot goldens + fidelity composites`

---

## Task 14: Delete `legacy-javafx-ui/` + final build

- [ ] **Step 1:** `git rm -r legacy-javafx-ui/` (the UI is fully reproduced + referenced; history preserves it).
- [ ] **Step 2:** `./gradlew clean build` (`:core` + `:desktop`) → green, all tests + snapshots pass, no reference to the deleted dir.
- [ ] **Step 3:** Confirm `find . -path ./.git -prune -o -name '*.kt' -print | xargs grep -l "import javafx\|import tornadofx"` returns nothing (no JavaFX anywhere in the tree).
- [ ] **Step 4: Commit** `chore: remove quarantined legacy JavaFX UI (fully ported to :desktop)`

---

## Self-Review (against spec §2–§9 + the snapshot recipe)

- **Spec coverage:** theme (T3), icons (T4), all components incl. Avatar-Gson + indeterminate progress (T5–T6), the 9×15 state model (T7), ViewModel + progress bridge (T8), main window + 4 snapshots (T9), settings + save-bug (T10), app entry/chrome/drag/multi-window/Sentry/BuildConfig (T11), Skiko uber jar (T12), snapshot validation both tracks (T2, T9, T10, T13). ✓
- **Bespoke (no Material):** TpTheme + foundation primitives; disabled = color swap. ✓
- **Faithful behavior:** state matrix verbatim; settings save-bug replicated; `delay(60s)` + error routing preserved. ✓
- **Snapshot tooling:** `renderComposeScene` spine (zero-dep), Roborazzi additive with explicit fallback, fidelity vs mockups = visual (not CI-fail), regression vs own goldens = CI gate, Linux-only goldens. ✓
- **Distribution:** single cross-platform Skiko jar keeps `upload.sh`/`launcher.json`. ✓
- **Placeholder scan:** SVG path strings are referenced from `legacy-javafx-ui/` source files (read verbatim by the implementer), not TBD — every code task shows code or the exact file+values to read. Component tasks pair code skeletons with the exact value tables + the snapshot fidelity gate that empirically enforces correctness.

## Execution Handoff

Plan 2 of 2. After approval, execute with superpowers:subagent-driven-development (fresh implementer per task + two-stage review, snapshot/unit gates per task, final whole-branch review), same as Plan 1. The live `:desktop:run` visual check + Track-A fidelity sign-off require a display — run those interactively.
