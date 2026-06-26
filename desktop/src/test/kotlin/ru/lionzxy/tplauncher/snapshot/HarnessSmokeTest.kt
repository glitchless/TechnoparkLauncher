package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class HarnessSmokeTest {
    @Test fun rendersWithCustomFont() {
        val f = snapshot("smoke", 960, 200) {
            BasicText(
                "games.glitchless.ru",
                modifier = Modifier.fillMaxSize().background(Color(0xFF36393E)),
                style = TextStyle(color = Color(0xFF00DB9D), fontFamily = SnapGugi),
            )
        }
        assertTrue("empty png", f.length() > 0)
    }
}
