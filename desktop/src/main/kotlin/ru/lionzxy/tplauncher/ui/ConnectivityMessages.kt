package ru.lionzxy.tplauncher.ui

import ru.lionzxy.tplauncher.minecraft.connectivity.AvClass

/**
 * Picks the user-facing guidance for a connectivity block from the detected security product:
 * Dr.Web-specific steps when Dr.Web is present, a generic "allow in your AV" message for other
 * third-party AVs, and the generic firewall/AV message otherwise.
 */
fun connectivityBlockedMessage(products: List<String>, avClass: AvClass): String = when {
    products.any { it.contains("Dr.Web", ignoreCase = true) || it.contains("drweb", ignoreCase = true) } ->
        Strings.drwebFirewallGuidance

    avClass == AvClass.THIRD_PARTY_NETWORK && products.isNotEmpty() ->
        Strings.thirdPartyAvGuidance(products.joinToString(", "))

    else -> Strings.connectionBlocked
}
