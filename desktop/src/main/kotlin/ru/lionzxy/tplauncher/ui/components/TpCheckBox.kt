package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens

/**
 * A 28×28dp checkbox using [TpColors.input] background.
 * When [checked], a [TpIcons.Check] icon (16dp, accent-tinted) is shown centred.
 * Visibility is derived purely from [checked] — no init flash.
 */
@Composable
fun TpCheckBox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TpDimens.checkbox)
            .clip(RoundedCornerShape(TpDimens.checkboxRadius))
            .background(TpColors.input)
            .clickable { onChange(!checked) },
    ) {
        if (checked) {
            Image(
                imageVector = TpIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(TpDimens.checkboxTick),
                colorFilter = ColorFilter.tint(TpColors.accent),
            )
        }
    }
}
