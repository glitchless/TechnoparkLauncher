package ru.lionzxy.tplauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lionzxy.tplauncher.ui.Strings
import ru.lionzxy.tplauncher.ui.theme.TpColors
import ru.lionzxy.tplauncher.ui.theme.TpDimens
import ru.lionzxy.tplauncher.ui.theme.TpTypography

/**
 * Repair panel shown for `LauncherState.ConnectivityBlocked`: the guidance [message] plus the actions.
 *
 * When [canFirewallFix] is true (Windows Defender / no third-party AV detected) the primary button
 * attempts the elevated Windows Firewall fix ("Разрешить доступ", one UAC prompt). For a third-party
 * AV like Dr.Web — which a firewall rule cannot unblock — only "Повторить" is shown, to be used after
 * the user has added an exception in the AV. "Повторить" also launches an already-installed pack via
 * the offline fallback even while the network is still blocked.
 */
@Composable
fun ConnectivityRepairPanel(
    message: String,
    canFirewallFix: Boolean,
    onFix: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TpDimens.gutter,
                end = TpDimens.margin,
                top = TpDimens.margin,
                bottom = TpDimens.margin,
            ),
    ) {
        BasicText(
            text = message,
            style = TpTypography.body.copy(color = TpColors.text),
        )
        Spacer(modifier = Modifier.height(TpDimens.margin))
        if (canFirewallFix) {
            TpButton(text = Strings.allowAccess, onClick = onFix)
            Spacer(modifier = Modifier.height(8.dp))
        }
        TpButton(text = Strings.retry, onClick = onRetry)
    }
}
