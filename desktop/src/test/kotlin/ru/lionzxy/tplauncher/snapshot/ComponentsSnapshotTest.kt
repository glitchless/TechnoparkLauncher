package ru.lionzxy.tplauncher.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.components.TpButton
import ru.lionzxy.tplauncher.ui.components.TpServerCombo
import ru.lionzxy.tplauncher.ui.components.TpTextField
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpTheme

class ComponentsSnapshotTest {

    @Test
    fun componentsSnapshot() {
        val f = snapshot("components_snapshot", 960, 700) {
            TpTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TpColors.background)
                        .padding(24.dp),
                ) {
                    // Login text field with value
                    var loginValue by remember { mutableStateOf("user@example.com") }
                    TpTextField(
                        value = loginValue,
                        onValueChange = { loginValue = it },
                        label = Strings.login,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password field
                    var passwordValue by remember { mutableStateOf("secret123") }
                    TpTextField(
                        value = passwordValue,
                        onValueChange = { passwordValue = it },
                        label = Strings.password,
                        password = true,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Server combo
                    var serverIndex by remember { mutableStateOf(0) }
                    TpServerCombo(
                        items = listOf("Vanilla", "NewHorizon", "Nomifactory"),
                        selectedIndex = serverIndex,
                        onSelect = { serverIndex = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Enabled button
                    TpButton(
                        text = Strings.enterGame,
                        enabled = true,
                        onClick = {},
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Disabled button
                    TpButton(
                        text = Strings.enterGame,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
        assertTrue("components snapshot PNG must be non-empty", f.length() > 0)
    }
}
