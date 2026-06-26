package ru.lionzxy.tplauncher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens

/**
 * A themed progress bar.
 *
 * - [value] in [0f, 1f] → determinate fill.
 * - [value] == -1f → indeterminate animated accent sweep.
 * - [enabled] == false → empty + input-colored track (no fill).
 */
@Composable
fun TpProgressBar(
    value: Float,
    enabled: Boolean,
) {
    val trackColor = if (enabled) TpColors.progressTrack else TpColors.input
    val shape = RoundedCornerShape(TpDimens.progressRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TpDimens.progressHeight)
            .clip(shape)
            .background(trackColor),
    ) {
        if (enabled) {
            when {
                value == -1f -> {
                    // Indeterminate: animate a sweeping accent segment
                    val infiniteTransition = rememberInfiniteTransition()
                    val offset by infiniteTransition.animateFloat(
                        initialValue = -0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1200, easing = LinearEasing),
                        ),
                    )
                    // Render the sweeping segment using a custom layout
                    IndeterminateFill(offset = offset)
                }

                else -> {
                    val fill = value.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .height(TpDimens.progressHeight)
                            .background(TpColors.accent),
                    )
                }
            }
        }
    }
}

/**
 * Renders a 40%-wide accent segment offset by [offset] (in track-width fractions) from the left.
 */
@Composable
private fun IndeterminateFill(offset: Float) {
    Layout(
        content = {
            Box(modifier = Modifier.background(TpColors.accent))
        },
    ) { measurables, constraints ->
        val trackWidth = constraints.maxWidth
        val segmentWidth = (trackWidth * 0.4f).toInt()
        val placeable = measurables[0].measure(
            constraints.copy(
                minWidth = segmentWidth,
                maxWidth = segmentWidth,
            ),
        )
        layout(trackWidth, constraints.maxHeight) {
            val x = (trackWidth * offset).toInt()
                .coerceIn(-segmentWidth, trackWidth)
            placeable.placeRelative(x = x, y = 0)
        }
    }
}
