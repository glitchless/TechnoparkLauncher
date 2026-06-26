package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

@Composable
fun TpButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TpDimens.buttonMinHeight)
            .clip(RoundedCornerShape(TpDimens.fieldRadius))
            .background(if (enabled) TpColors.accent else TpColors.input)
            .clickable(enabled = enabled) { onClick() }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TpTypography.button.copy(
                color = if (enabled) TpColors.text else TpColors.disable,
            ),
        )
    }
}
