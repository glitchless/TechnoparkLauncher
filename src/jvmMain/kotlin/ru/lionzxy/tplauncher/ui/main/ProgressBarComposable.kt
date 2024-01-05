package ru.lionzxy.tplauncher.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lionzxy.tplauncher.ui.Constants
import ru.lionzxy.tplauncher.ui.common.GlitchlessFonts

@Composable
fun ProgressBarComposable(
    progressText: String?,
    progressTextColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Constants.backgroundDarkColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.padding(
                top = 11.5.dp
            ),
            text = progressText ?: "",
            color = progressTextColor,
            textAlign = TextAlign.Center,
            fontFamily = GlitchlessFonts.roboto,
            fontSize = 14.sp
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
                .padding(
                    bottom = 18.5.dp,
                    start = 22.dp,
                    end = 22.dp,
                    top = 7.dp
                )
                .height(12.5.dp),
            color = Constants.accentColor,
            backgroundColor = Constants.backgroundProgressBarColor,
            strokeCap = StrokeCap.Square
        )
    }
}