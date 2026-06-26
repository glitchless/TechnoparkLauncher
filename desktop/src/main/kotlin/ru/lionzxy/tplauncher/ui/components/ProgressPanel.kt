package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

/**
 * A dark panel with centered [text] above a [TpProgressBar].
 *
 * [value] semantics match [TpProgressBar]: 0f–1f determinate, -1f indeterminate.
 */
@Composable
fun ProgressPanel(
    text: String,
    textColor: Color,
    value: Float,
    enabled: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TpDimens.margin),
        modifier = Modifier
            .fillMaxWidth()
            .background(TpColors.backgroundDark)
            .padding(TpDimens.margin),
    ) {
        BasicText(
            text = text,
            style = TpTypography.body.copy(color = textColor),
        )
        TpProgressBar(
            value = value,
            enabled = enabled,
        )
    }
}
