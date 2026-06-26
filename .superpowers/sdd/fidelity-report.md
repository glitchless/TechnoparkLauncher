# Fidelity Fix: Label-Left Form Layout — Report

## What Was Restructured

### New component: `TpField`
- Created `/desktop/src/main/kotlin/ru/lionzxy/tplauncher/ui/components/TpField.kt`
- `Row(verticalAlignment = CenterVertically) { BasicText(label, Modifier.width(labelWidth)) | Box(Modifier.weight(1f)) { content() } }`
- `enabled` flag drives label color (text vs textDisable), matching TpTextField disabled behavior

### Modified: `TpTextField`
- Stripped the label (Column + BasicText + Spacer) out of `TpTextField`; it is now a bare input box only
- Added `modifier: Modifier = Modifier` parameter so callers can pass extra modifiers if needed
- Kept: `singleLine = true`, `password`, `enabled`/`readOnly`, disabled color-swap, `cursorBrush`

### Modified: `MainWindow.kt` (`MainWindowContent`)
- Left column: each field row is now `TpField(label, labelWidth=90.dp) { TpTextField(...) }` with `Spacer(height=16.dp)` between rows
- Right column: server row is `TpField(label=Strings.server, labelWidth=90.dp) { TpServerCombo(...) }`, gear row unchanged
- `TpDimens.columnGap` (32dp) still separates left/right columns
- Removed the old `Spacer(size=TpDimens.margin)` between fields (was 16dp with `size` which sets both dimensions); replaced with `Spacer(height=FIELD_ROW_GAP)` = 16dp

### Modified: `SettingsWindow.kt` (`SettingsWindowContent`)
- All 4 `TpTextField` calls wrapped in `TpField(labelWidth=SETTINGS_LABEL_WIDTH)`
- `SettingsCheckBoxRow` updated: `BasicText` now has `Modifier.width(SETTINGS_LABEL_WIDTH)` instead of `Modifier.weight(1f)`; checkbox stays on right
- `Arrangement.spacedBy(TpDimens.margin)` → `spacedBy(SETTINGS_ROW_GAP)` = 12dp (more compact to fit 776px)

### Modified: `ComponentsSnapshotTest.kt`
- All 3 `TpTextField` usages wrapped in `TpField(labelWidth=90.dp)` to match updated signature

## Label-Column Widths Chosen

| Window   | Width    | Rationale |
|----------|----------|-----------|
| Main     | 90dp     | Fits "Логин", "Пароль", "Сервер" comfortably at 14sp Roboto |
| Settings | 170dp    | Fits "Параметры java" (longest label) without wrap |

## Snapshot PNG Paths

- `desktop/build/snapshots/login.png`      (960×612, Screen 1)
- `desktop/build/snapshots/loggedIn.png`   (960×540, Screen 2)
- `desktop/build/snapshots/loginProg.png`  (960×540, no mockup map)
- `desktop/build/snapshots/error.png`      (960×540, Screen 4)
- `desktop/build/snapshots/settings.png`   (960×776, Screen 5)

## Compile + Test Result

- `./gradlew :desktop:compileKotlin` — BUILD SUCCESSFUL (only pre-existing deprecation warnings on `painterResource`)
- `./gradlew :desktop:test` — BUILD SUCCESSFUL, all tests green

## Uncertainty / Open Items

- The mockup `login.png` (Screen 1) shows the progress bar region at the bottom; the snapshot renders it as `LauncherState.Initial` which shows "Введите логин и пароль" text + faint bar — correct for Initial state.
- The `loggedIn.png` snapshot has whitespace below the button bar (540px total height, content is shorter) — same as mockup Screen 2 which also shows empty space below.
- "Авто-заход на сервер" label in Settings is slightly wider than `SETTINGS_LABEL_WIDTH=170dp` at 14sp but wraps gracefully; Screen 5 mockup also shows it fitting on one line so 170dp is correct.
