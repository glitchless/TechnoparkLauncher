package ru.lionzxy.tplauncher.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.lionzxy.tplauncher.ui.Constants
import ru.lionzxy.tplauncher.ui.common.GlitchlessFonts
import ru.lionzxy.tplauncher.ui.main.model.BaseState

@Composable
fun MainComposable(state: BaseState, onClose: () -> Unit) {
    Box(
        modifier = Modifier.width(480.dp)
            .background(Constants.backgroundColor),
        contentAlignment = Alignment.TopEnd
    ) {
        Icon(
            modifier = Modifier
                .padding(
                    top = 11.5.dp,
                    end = 20.5.dp
                )
                .size(16.5.dp)
                .clickable(onClick = onClose),
            painter = painterResource("icon/cross.svg"),
            contentDescription = "Close",
            tint = Constants.disableColor
        )
        Column(Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(top = 11.5.dp, start = 23.dp),
                text = "games.glitchless.ru",
                fontFamily = GlitchlessFonts.gugi,
                fontSize = 30.sp,
                color = state.titleColor
            )
            EnterGameButtonComposable(
                modifier = Modifier.padding(
                    top = 18.5.dp,
                    bottom = 15.5.dp,
                    start = 22.dp,
                    end = 22.dp
                ),
                text = state.buttonText,
                onClick = {}
            )
            ProgressBarComposable(
                progressText = state.progressTextContent,
                progressTextColor = state.progressTextColor
            )
        }
    }
}