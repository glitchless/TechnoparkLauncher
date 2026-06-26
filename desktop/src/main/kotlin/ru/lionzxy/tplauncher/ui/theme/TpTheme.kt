package ru.lionzxy.tplauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TpColors {
    val accent = Color(0xFF00DB9D)
    val text = Color(0xFFFFFFFF)
    val background = Color(0xFF36393E)
    val backgroundDark = Color(0xFF2F3136)
    val backgroundCircle = Color(0xFF2B2C31)
    val input = Color(0xFF484C51)
    val progressTrack = Color(0xFFDDDDDE)
    val disable = Color(0xFF7F8185)
    val textDisable = Color(0xFFAAABAD)
    val error = Color(0xFFD75379)
}

private val Gugi = FontFamily(Font("fonts/Gugi-Regular.ttf", FontWeight.Normal))
private val Roboto = FontFamily(Font("fonts/Roboto-Regular.ttf", FontWeight.Normal))

object TpTypography {
    val title = TextStyle(fontFamily = Gugi, fontSize = 30.sp, color = TpColors.accent)
    val body = TextStyle(fontFamily = Roboto, fontSize = 14.sp, color = TpColors.text)
    val button = TextStyle(fontFamily = Roboto, fontSize = 16.sp, color = TpColors.text)
    val caption = TextStyle(fontFamily = Roboto, fontSize = 12.sp, color = TpColors.textDisable)
}

object TpDimens {
    val margin = 16.dp
    val columnGap = 32.dp
    val gutter = 23.dp
    val titleTop = 11.5.dp
    val windowWidth = 592.dp
    val avatar = 84.dp
    val avatarCheck = 42.dp
    val gear = 34.dp
    val checkbox = 28.dp
    val checkboxTick = 16.dp
    val closeX = 20.dp
    val buttonMinHeight = 36.dp
    val progressHeight = 12.dp
    val progressRadius = 5.dp
    val fieldRadius = 3.dp
    val checkboxRadius = 2.5.dp
    val backButtonRadius = 3.5.dp
}

val LocalTpColors = staticCompositionLocalOf { TpColors }
val LocalTpTypography = staticCompositionLocalOf { TpTypography }
val LocalTpDimens = staticCompositionLocalOf { TpDimens }

@Composable
fun TpTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTpColors provides TpColors,
        LocalTpTypography provides TpTypography,
        LocalTpDimens provides TpDimens,
        content = content,
    )
}
