package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

@Composable
fun TpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            text = label,
            style = TpTypography.body.copy(
                color = if (enabled) TpColors.text else TpColors.textDisable,
            ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TpDimens.fieldRadius))
                .background(TpColors.input)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                readOnly = !enabled,
                singleLine = true,
                textStyle = TpTypography.body.copy(
                    color = if (enabled) TpColors.text else TpColors.textDisable,
                ),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(TpColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
