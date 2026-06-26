package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.icons.TpIcons

class IconSnapshotTest {

    private val bgColor = Color(0xFF36393E)
    private val timesColor = Color(0xFF7F8185)
    private val cogsColor = Color.White
    private val checkColor = Color(0xFF00DB9D)
    private val chevronColor = Color.White

    @Test
    fun iconsRenderOnDarkBackground() {
        val f = snapshot("icons_tinted", 400, 100) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    imageVector = TpIcons.Times,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(timesColor),
                )
                Image(
                    imageVector = TpIcons.Cogs,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(cogsColor),
                )
                Image(
                    imageVector = TpIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(checkColor),
                )
                Image(
                    imageVector = TpIcons.Chevron,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(chevronColor),
                )
            }
        }
        assertTrue("icons png must be non-empty", f.length() > 0)
    }
}
