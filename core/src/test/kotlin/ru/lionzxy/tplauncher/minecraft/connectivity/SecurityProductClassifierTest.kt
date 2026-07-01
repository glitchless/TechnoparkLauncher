package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityProductClassifierTest {
    @Test
    fun drwebIsThirdPartyNetwork() =
        assertEquals(AvClass.THIRD_PARTY_NETWORK, SecurityProductClassifier.classify(listOf("Dr.Web Security Space")).avClass)

    @Test
    fun kasperskyIsThirdPartyNetwork() =
        assertEquals(AvClass.THIRD_PARTY_NETWORK, SecurityProductClassifier.classify(listOf("Kaspersky Internet Security")).avClass)

    @Test
    fun defenderOnlyIsDefender() =
        assertEquals(AvClass.DEFENDER_ONLY, SecurityProductClassifier.classify(listOf("Windows Defender")).avClass)

    @Test
    fun emptyIsNoneDetected() =
        assertEquals(AvClass.NONE_DETECTED, SecurityProductClassifier.classify(emptyList()).avClass)

    @Test
    fun blankOnlyIsNoneDetected() =
        assertEquals(AvClass.NONE_DETECTED, SecurityProductClassifier.classify(listOf("  ", "")).avClass)

    @Test
    fun defenderPlusThirdPartyIsThirdParty() =
        assertEquals(
            AvClass.THIRD_PARTY_NETWORK,
            SecurityProductClassifier.classify(listOf("Windows Defender", "Dr.Web")).avClass,
        )

    @Test
    fun preservesTrimmedProductNames() =
        assertEquals(listOf("Dr.Web"), SecurityProductClassifier.classify(listOf(" Dr.Web ")).products)
}
