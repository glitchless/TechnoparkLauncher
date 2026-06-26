package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpTheme
import ru.lionzxy.tplauncher.ui.theme.TpTypography

class ThemeSnapshotTest {

    @Test
    fun themeSnapshot() {
        val f = snapshot("theme_snapshot", 960, 600) {
            TpTheme {
                Column(
                    modifier = Modifier
                        .background(TpColors.background)
                        .padding(16.dp)
                ) {
                    // Title style
                    BasicText("Technopark Launcher", style = TpTypography.title)

                    Spacer(Modifier.height(12.dp))

                    // Body style
                    BasicText("Body text — Roboto 14sp white", style = TpTypography.body)

                    Spacer(Modifier.height(8.dp))

                    // Button style
                    BasicText("BUTTON TEXT — Roboto 16sp", style = TpTypography.button)

                    Spacer(Modifier.height(8.dp))

                    // Caption style
                    BasicText("Caption — Roboto 12sp disabled", style = TpTypography.caption)

                    Spacer(Modifier.height(16.dp))

                    // Color swatches
                    listOf(
                        "accent" to TpColors.accent,
                        "background" to TpColors.background,
                        "backgroundDark" to TpColors.backgroundDark,
                        "backgroundCircle" to TpColors.backgroundCircle,
                        "input" to TpColors.input,
                        "progressTrack" to TpColors.progressTrack,
                        "disable" to TpColors.disable,
                        "textDisable" to TpColors.textDisable,
                        "error" to TpColors.error,
                    ).forEach { (label, color) ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color)
                            )
                            Spacer(Modifier.size(8.dp))
                            BasicText(
                                label,
                                style = TpTypography.body,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        assertTrue("theme snapshot PNG must be non-empty", f.length() > 0)
    }
}
