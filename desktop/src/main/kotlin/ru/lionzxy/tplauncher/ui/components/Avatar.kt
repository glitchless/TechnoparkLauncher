package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import ru.lionzxy.tplauncher.ui.loadAvatar
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.utils.ConfigHelper

/**
 * Circular 84dp avatar.
 *
 * Reads [ConfigHelper.config.profile?.login] internally.
 * - If null → placeholder (backgroundCircle + check icon), no network call.
 * - If non-null → attempts to load via [loadAvatar]; shows placeholder until loaded.
 */
@Composable
fun Avatar(modifier: Modifier = Modifier) {
    val login = ConfigHelper.config.profile?.login

    val bitmap = produceState<ImageBitmap?>(initialValue = null, key1 = login) {
        value = login?.let { loadAvatar(it) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TpDimens.avatar)
            .clip(CircleShape),
    ) {
        val loaded = bitmap.value
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(TpDimens.avatar)
                    .clip(CircleShape),
            )
        } else {
            // Placeholder
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(TpDimens.avatar)
                    .background(TpColors.backgroundCircle),
            ) {
                Image(
                    imageVector = TpIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(TpDimens.avatarCheck),
                    colorFilter = ColorFilter.tint(TpColors.accent),
                )
            }
        }
    }
}
