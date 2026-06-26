package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import ru.lionzxy.tplauncher.ui.icons.TpIcons
import ru.lionzxy.tplauncher.ui.loadAvatar
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.utils.ConfigHelper

/**
 * Circular 84dp avatar for the real app. Reads [ConfigHelper.config.profile]'s login internally:
 * - login null  → placeholder (no network), shows the mint-check circle
 * - login set   → loads via [loadAvatar]; placeholder until the bitmap arrives
 *
 * Rendering is delegated to the pure [AvatarContent] so snapshots stay deterministic
 * (they render AvatarContent directly with a fixed bitmap, never touching config/disk/network).
 */
@Composable
fun Avatar(modifier: Modifier = Modifier) {
    val login = ConfigHelper.config.profile?.login
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = login) {
        value = login?.let { loadAvatar(it) }
    }
    AvatarContent(bitmap = bitmap, modifier = modifier)
}

/**
 * Pure, deterministic avatar rendering — no config/IO. [bitmap] null → mint-check placeholder
 * (the look used by the logged-in mockup, Screen 2); non-null → the circle-clipped image.
 */
@Composable
fun AvatarContent(bitmap: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TpDimens.avatar)
            .clip(CircleShape),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(TpDimens.avatar)
                    .clip(CircleShape),
            )
        } else {
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
