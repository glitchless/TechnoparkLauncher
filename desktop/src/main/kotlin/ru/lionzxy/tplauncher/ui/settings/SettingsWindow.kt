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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.CloseX
import ru.lionzxy.tplauncher.ui.components.Title
import ru.lionzxy.tplauncher.ui.components.TpButton
import ru.lionzxy.tplauncher.ui.components.TpCheckBox
import ru.lionzxy.tplauncher.ui.components.TpTextField
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

/**
 * Settings window content composable.
 *
 * Pure / stateless — all mutable state lives in [SettingsViewModel].
 * Wired to the OS window in Task 11.
 *
 * Layout (matches Screen 5):
 *   - Title row (accent, or error-red when heapError != null) + CloseX overlay top-right
 *   - 4 TpTextField rows: Объем памяти, Параметры java, Prefix, Путь до Java
 *   - 2 TpCheckBox rows: Дебаг-режим, Авто-заход на сервер
 *   - 4 action links (last one in muted textDisable)
 *   - Bottom bar (backgroundDark): "Вернуться" ghost button + "Применить" accent button
 */
@Composable
fun SettingsWindowContent(vm: SettingsViewModel) {
    val heapError by vm.heapError.collectAsState()
    val titleColor = if (heapError != null) TpColors.error else TpColors.accent

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

            // ── Form fields ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TpDimens.gutter, end = TpDimens.margin),
                verticalArrangement = Arrangement.spacedBy(TpDimens.margin),
            ) {
                TpTextField(
                    value = vm.heap,
                    onValueChange = vm::onHeapChange,
                    label = Strings.memorySize,
                )

                TpTextField(
                    value = vm.javaArgs,
                    onValueChange = vm::onJavaArgsChange,
                    label = Strings.javaParams,
                )

                TpTextField(
                    value = vm.prefix,
                    onValueChange = vm::onPrefixChange,
                    label = Strings.prefix,
                )

                TpTextField(
                    value = vm.javaPath,
                    onValueChange = vm::onJavaPathChange,
                    label = Strings.javaPath,
                )

                // ── Дебаг-режим ──────────────────────────────────────────────
                SettingsCheckBoxRow(
                    label = Strings.debugMode,
                    checked = vm.debug,
                    onChange = vm::onDebugChange,
                )

                // ── Авто-заход на сервер ─────────────────────────────────────
                SettingsCheckBoxRow(
                    label = Strings.autoJoinServer,
                    checked = vm.autoJoin,
                    onChange = vm::onAutoJoinChange,
                )
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
 * A labeled row with label on the left and a [TpCheckBox] on the right side.
 */
@Composable
private fun SettingsCheckBoxRow(
    label: String,
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
            modifier = Modifier.weight(1f),
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
