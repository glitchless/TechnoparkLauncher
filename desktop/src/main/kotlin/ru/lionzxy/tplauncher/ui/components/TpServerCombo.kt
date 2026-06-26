package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

@Composable
fun TpServerCombo(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // The trigger row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { anchorWidthPx = it.size.width }
                .clip(RoundedCornerShape(TpDimens.fieldRadius))
                .background(TpColors.input)
                .clickable(enabled = enabled) { expanded = !expanded }
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicText(
                    text = if (items.isNotEmpty()) items.getOrElse(selectedIndex) { items[0] } else "",
                    style = TpTypography.body.copy(
                        color = if (enabled) TpColors.text else TpColors.disable,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Image(
                    imageVector = TpIcons.Chevron,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(if (enabled) TpColors.text else TpColors.disable),
                )
            }
        }

        // Dropdown popup — sized to the anchor (combo) width, not the window width.
        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(with(LocalDensity.current) { anchorWidthPx.toDp() })
                        .clip(RoundedCornerShape(TpDimens.fieldRadius))
                        .background(TpColors.input),
                ) {
                    items.forEachIndexed { index, item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(index)
                                    expanded = false
                                }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            BasicText(
                                text = item,
                                style = TpTypography.body.copy(color = TpColors.text),
                            )
                        }
                    }
                }
            }
        }
    }
}
