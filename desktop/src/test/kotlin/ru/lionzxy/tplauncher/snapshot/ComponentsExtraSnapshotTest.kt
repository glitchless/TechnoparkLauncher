package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.components.Avatar
import ru.lionzxy.tplauncher.ui.components.CloseX
import ru.lionzxy.tplauncher.ui.components.GearRow
import ru.lionzxy.tplauncher.ui.components.ProgressPanel
import ru.lionzxy.tplauncher.ui.components.RegisterLink
import ru.lionzxy.tplauncher.ui.components.Title
import ru.lionzxy.tplauncher.ui.components.TpCheckBox
import ru.lionzxy.tplauncher.ui.components.TpProgressBar
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpTheme
import ru.lionzxy.tplauncher.ui.theme.TpTypography

class ComponentsExtraSnapshotTest {

    @Test
    fun componentsExtraSnapshot() {
        val f = snapshot("components_extra_snapshot", 960, 1400) {
            TpTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TpColors.background)
                        .padding(24.dp),
                ) {
                    // ── Checkboxes ────────────────────────────────────────────
                    BasicText("TpCheckBox — unchecked + checked", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var unchecked by remember { mutableStateOf(false) }
                        TpCheckBox(checked = unchecked, onChange = { unchecked = it })

                        Spacer(modifier = Modifier.width(16.dp))

                        var checked by remember { mutableStateOf(true) }
                        TpCheckBox(checked = checked, onChange = { checked = it })
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Avatar placeholder ────────────────────────────────────
                    // ConfigHelper.config.profile is null in the test environment
                    // → Avatar renders the placeholder; no network call is made.
                    BasicText("Avatar — placeholder (no profile in test)", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    Avatar()

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── TpProgressBar: determinate ────────────────────────────
                    BasicText("TpProgressBar — determinate ~0.6, enabled", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    TpProgressBar(value = 0.6f, enabled = true)

                    Spacer(modifier = Modifier.height(16.dp))

                    BasicText("TpProgressBar — indeterminate (-1f), enabled", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    TpProgressBar(value = -1f, enabled = true)

                    Spacer(modifier = Modifier.height(16.dp))

                    BasicText("TpProgressBar — disabled", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    TpProgressBar(value = 0.4f, enabled = false)

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── ProgressPanel ─────────────────────────────────────────
                    BasicText("ProgressPanel", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    ProgressPanel(
                        text = "Загружаем игру...",
                        textColor = TpColors.text,
                        value = 0.5f,
                        enabled = true,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Chrome: CloseX ────────────────────────────────────────
                    BasicText("Chrome: CloseX", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    CloseX(onClick = {})

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Chrome: Title ─────────────────────────────────────────
                    BasicText("Chrome: Title", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    Title(color = TpColors.accent)

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Chrome: RegisterLink ──────────────────────────────────
                    BasicText("Chrome: RegisterLink (accent)", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    RegisterLink(color = TpColors.accent, onClick = {})

                    Spacer(modifier = Modifier.height(8.dp))

                    BasicText("Chrome: RegisterLink (error)", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    RegisterLink(color = TpColors.error, onClick = {})

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Chrome: GearRow ───────────────────────────────────────
                    BasicText("Chrome: GearRow — enabled", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    GearRow(enabled = true, onClick = {})

                    Spacer(modifier = Modifier.height(8.dp))

                    BasicText("Chrome: GearRow — disabled", style = TpTypography.caption)
                    Spacer(modifier = Modifier.height(8.dp))
                    GearRow(enabled = false, onClick = {})
                }
            }
        }
        assertTrue("components_extra snapshot PNG must be non-empty", f.length() > 0)
    }
}
