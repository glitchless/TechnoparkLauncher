package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpTypography

/**
 * Inline label-left field wrapper that matches the TornadoFX `field("label") { input }`
 * layout from the legacy UI.
 *
 * @param label      Label text shown to the left of the input.
 * @param labelWidth Fixed width of the label column (tune per window).
 * @param enabled    When false the label is rendered in the disabled colour.
 * @param modifier   Applied to the outer [Row].
 * @param content    The input widget (e.g. [TpTextField], [TpServerCombo]).
 */
@Composable
fun TpField(
    label: String,
    labelWidth: Dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = label,
            style = TpTypography.body.copy(
                color = if (enabled) TpColors.text else TpColors.textDisable,
            ),
            modifier = Modifier.width(labelWidth),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
