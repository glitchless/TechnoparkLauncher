package ru.lionzxy.tplauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.lionzxy.tplauncher.minecraft.connectivity.AvClass

class ConnectivityMessagesTest {
    @Test
    fun drwebGetsDrwebGuidance() =
        assertEquals(
            Strings.drwebFirewallGuidance,
            connectivityBlockedMessage(listOf("Dr.Web Security Space"), AvClass.THIRD_PARTY_NETWORK),
        )

    @Test
    fun otherThirdPartyGetsGenericThirdParty() =
        assertEquals(
            Strings.thirdPartyAvGuidance("Kaspersky"),
            connectivityBlockedMessage(listOf("Kaspersky"), AvClass.THIRD_PARTY_NETWORK),
        )

    @Test
    fun defenderGetsGenericBlocked() =
        assertEquals(Strings.connectionBlocked, connectivityBlockedMessage(listOf("Windows Defender"), AvClass.DEFENDER_ONLY))

    @Test
    fun emptyGetsGenericBlocked() =
        assertEquals(Strings.connectionBlocked, connectivityBlockedMessage(emptyList(), AvClass.NONE_DETECTED))
}
