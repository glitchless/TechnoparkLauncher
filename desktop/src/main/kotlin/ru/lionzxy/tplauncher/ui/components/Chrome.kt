package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

/**
 * The X-close button in the window chrome.
 * Uses [TpIcons.Times] (20dp), tinted with [TpColors.disable].
 */
@Composable
fun CloseX(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        imageVector = TpIcons.Times,
        contentDescription = null,
        modifier = modifier
            .size(TpDimens.closeX)
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand),
        colorFilter = ColorFilter.tint(TpColors.disable),
    )
}

/**
 * The server-address title text.
 * Uses [TpTypography.title] with the supplied [color].
 */
@Composable
fun Title(
    color: Color = TpColors.accent,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = Strings.serverAddress,
        style = TpTypography.title.copy(color = color),
        modifier = modifier,
    )
}

/**
 * A clickable registration link styled with [TpTypography.body], underlined.
 * [color] defaults to accent; pass [TpColors.error] for an error state.
 */
@Composable
fun RegisterLink(
    color: Color = TpColors.accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = Strings.registerOnSite,
        style = TpTypography.body.copy(
            color = color,
            textDecoration = TextDecoration.Underline,
        ),
        modifier = modifier
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon.Hand),
    )
}

/**
 * A row showing the gear icon + "Настройки запуска" label.
 * Clickable (with hand cursor) only when [enabled].
 */
@Composable
fun GearRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) TpColors.text else TpColors.disable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
    ) {
        Image(
            imageVector = TpIcons.Cogs,
            contentDescription = null,
            modifier = Modifier.size(TpDimens.gear),
            colorFilter = ColorFilter.tint(tint),
        )
        Spacer(modifier = Modifier.width(TpDimens.margin))
        BasicText(
            text = Strings.launchSettings,
            style = TpTypography.body.copy(color = tint),
        )
    }
}
