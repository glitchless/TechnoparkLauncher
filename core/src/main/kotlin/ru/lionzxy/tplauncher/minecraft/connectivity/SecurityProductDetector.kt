package ru.lionzxy.tplauncher.minecraft.connectivity

/**
 * How fixable a detected security setup is from the launcher's side:
 * - [THIRD_PARTY_NETWORK]: a third-party AV (Dr.Web, Kaspersky, …) whose own network filter blocks
 *   the socket — a Windows Firewall allow-rule will NOT help; the user must add an exception in the AV.
 * - [DEFENDER_ONLY]: only Microsoft/Windows Defender present — the firewall auto-fix is the right action.
 * - [NONE_DETECTED]: nothing found (or detection failed) — treat like Defender-only.
 */
enum class AvClass { THIRD_PARTY_NETWORK, DEFENDER_ONLY, NONE_DETECTED }

data class DetectedSecurity(val products: List<String>, val avClass: AvClass)

interface SecurityProductDetector {
    /** Installed AV products + classification. Best-effort; returns NONE_DETECTED on failure. */
    fun detect(): DetectedSecurity
}

/** Pure display-name → [AvClass] mapping, unit-tested independently of the OS/WMI query. */
object SecurityProductClassifier {
    private val THIRD_PARTY = listOf(
        "dr.web", "drweb", "kaspersky", "eset", "nod32", "avast", "avg", "norton", "comodo",
    )

    fun classify(displayNames: List<String>): DetectedSecurity {
        val products = displayNames.map { it.trim() }.filter { it.isNotEmpty() }
        val lower = products.map { it.lowercase() }
        return when {
            lower.any { name -> THIRD_PARTY.any { it in name } } ->
                DetectedSecurity(products, AvClass.THIRD_PARTY_NETWORK)

            products.isEmpty() -> DetectedSecurity(products, AvClass.NONE_DETECTED)
            else -> DetectedSecurity(products, AvClass.DEFENDER_ONLY)
        }
    }
}
