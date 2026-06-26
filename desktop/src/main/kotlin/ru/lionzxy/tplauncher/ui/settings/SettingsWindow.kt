package ru.lionzxy.tplauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.CloseX
import ru.lionzxy.tplauncher.ui.components.Title
import ru.lionzxy.tplauncher.ui.components.TpButton
import ru.lionzxy.tplauncher.ui.components.TpCheckBox
import ru.lionzxy.tplauncher.ui.components.TpField
import ru.lionzxy.tplauncher.ui.components.TpServerCombo
import ru.lionzxy.tplauncher.ui.components.TpTextField
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

// Label column wide enough for the longest label ("Авто-заход на сервер") at the
// 18.67sp body size — the legacy TornadoFX form auto-sized the column to the longest label.
private val SETTINGS_LABEL_WIDTH = 225.dp

// Vertical spacing between field rows in Settings (compact to fit all in ~776px)
private val SETTINGS_ROW_GAP = 12.dp

// UI-scale options offered in Settings. Values multiply the whole-UI density;
// labels are the user-facing "x.." captions shown in the dropdown.
val UI_SCALE_OPTIONS = listOf(0.5f, 1f, 2f, 4f, 8f, 16f)
val UI_SCALE_LABELS = listOf("x0.5", "x1", "x2", "x4", "x8", "x16")

/**
 * Settings window content composable.
 *
 * Pure / stateless — all mutable state lives in [SettingsViewModel].
 * Wired to the OS window in Task 11.
 *
 * Layout (matches Screen 5):
 *   - Title row (accent, or error-red when heapError != null) + CloseX overlay top-right
 *   - 4 TpField rows (label-left): Объем памяти, Параметры java, Prefix, Путь до Java
 *   - 2 checkbox rows (label-left): Дебаг-режим, Авто-заход на сервер
 *   - 4 action links (last one in muted textDisable)
 *   - Bottom bar (backgroundDark): "Вернуться" ghost button + "Применить" accent button
 */
@Composable
fun SettingsWindowContent(
    vm: SettingsViewModel,
    currentScale: Float = 1f,
    onScaleChange: (Float) -> Unit = {},
) {
    val heapError by vm.heapError.collectAsState()
    val titleColor = if (heapError != null) TpColors.error else TpColors.accent
    val scaleIndex = UI_SCALE_OPTIONS.indexOf(currentScale).let { if (it < 0) 1 else it }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TpColors.background),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Title ────────────────────────────────────────────────────────
            Title(
                color = titleColor,
                modifier = Modifier.padding(top = TpDimens.titleTop, start = TpDimens.gutter),
            )

            Spacer(modifier = Modifier.height(TpDimens.margin))

            // ── Form fields (label-left) ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TpDimens.gutter, end = TpDimens.margin),
                verticalArrangement = Arrangement.spacedBy(SETTINGS_ROW_GAP),
            ) {
                TpField(label = Strings.memorySize, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.heap,
                        onValueChange = vm::onHeapChange,
                    )
                }

                TpField(label = Strings.javaParams, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.javaArgs,
                        onValueChange = vm::onJavaArgsChange,
                    )
                }

                TpField(label = Strings.prefix, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.prefix,
                        onValueChange = vm::onPrefixChange,
                    )
                }

                TpField(label = Strings.javaPath, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpTextField(
                        value = vm.javaPath,
                        onValueChange = vm::onJavaPathChange,
                    )
                }

                // ── Дебаг-режим ──────────────────────────────────────────────
                SettingsCheckBoxRow(
                    label = Strings.debugMode,
                    labelWidth = SETTINGS_LABEL_WIDTH,
                    checked = vm.debug,
                    onChange = vm::onDebugChange,
                )

                // ── Авто-заход на сервер ─────────────────────────────────────
                SettingsCheckBoxRow(
                    label = Strings.autoJoinServer,
                    labelWidth = SETTINGS_LABEL_WIDTH,
                    checked = vm.autoJoin,
                    onChange = vm::onAutoJoinChange,
                )

                // ── Масштаб интерфейса — scales the whole UI (applies live) ──
                TpField(label = Strings.uiScale, labelWidth = SETTINGS_LABEL_WIDTH) {
                    TpServerCombo(
                        items = UI_SCALE_LABELS,
                        selectedIndex = scaleIndex,
                        onSelect = { onScaleChange(UI_SCALE_OPTIONS[it]) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(TpDimens.margin))

            // ── Action links ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TpDimens.gutter, bottom = TpDimens.margin),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionLink(
                    text = Strings.goToGameDirectory,
                    color = TpColors.accent,
                    onClick = vm::openGameDir,
                )
                ActionLink(
                    text = Strings.logout,
                    color = TpColors.accent,
                    onClick = vm::logout,
                )
                ActionLink(
                    text = vm.backupSizeLabel,
                    color = TpColors.accent,
                    onClick = vm::clearBackup,
                )
                ActionLink(
                    text = Strings.deleteGameAndReset,
                    color = TpColors.textDisable,
                    onClick = vm::wipe,
                )
            }

            // ── Bottom button row (backgroundDark) ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TpColors.backgroundDark)
                    .padding(TpDimens.margin),
                horizontalArrangement = Arrangement.spacedBy(TpDimens.margin),
            ) {
                // Вернуться — ghost button (transparent bg, 1dp accent border, accent text)
                GhostButton(
                    text = Strings.back,
                    onClick = vm::back,
                    modifier = Modifier.weight(1f),
                )

                // Применить — accent TpButton
                TpButton(
                    text = Strings.apply,
                    onClick = vm::apply,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Close X (top-right overlay) — wired to close the window (Task 11) ──
        CloseX(
            onClick = vm::back,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(TpDimens.margin),
        )
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * A labeled row with label on the left (fixed width) and a [TpCheckBox] on the right side.
 * Matches Screen 5: [label]  [checkbox].
 */
@Composable
private fun SettingsCheckBoxRow(
    label: String,
    labelWidth: Dp,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BasicText(
            text = label,
            style = TpTypography.body,
            modifier = Modifier.width(labelWidth),
        )
        TpCheckBox(
            checked = checked,
            onChange = onChange,
        )
    }
}

/**
 * A clickable label rendered as a link (underlined).
 */
@Composable
private fun ActionLink(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        style = TpTypography.body.copy(
            color = color,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = Modifier
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand),
    )
}

/**
 * Ghost / outline button: transparent background, 1dp [TpColors.accent] border,
 * accent-colored text, [TpDimens.backButtonRadius] corner radius.
 * Used for the "Вернуться" action in the settings window.
 */
@Composable
private fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TpDimens.backButtonRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TpDimens.buttonMinHeight)
            .clip(shape)
            .border(width = 1.dp, color = TpColors.accent, shape = shape)
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TpTypography.button.copy(color = TpColors.accent),
        )
    }
}
