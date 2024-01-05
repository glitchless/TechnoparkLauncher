package ru.lionzxy.tplauncher.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lionzxy.tplauncher.ui.Constants
import ru.lionzxy.tplauncher.ui.common.GlitchlessFonts

@Composable
fun EnterGameButtonComposable(
    modifier: Modifier,
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth()
            .height(36.5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Constants.accentColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = GlitchlessFonts.roboto,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = Constants.secondColor
        )
    }
}