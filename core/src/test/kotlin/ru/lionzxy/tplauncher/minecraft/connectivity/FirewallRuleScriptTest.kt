package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirewallRuleScriptTest {
    private val marker = File("C:\\tmp\\repair.done")

    @Test
    fun addsOutboundAllowRulePerBinary() {
        val bin = File("C:\\Games\\javaw.exe")
        val s = FirewallRuleScript.build(listOf(bin), marker)
        assertTrue(s.contains("advfirewall firewall add rule"))
        assertTrue(s.contains("dir=out action=allow"))
        // The builder quotes whatever absolutePath yields (a real Windows path at runtime); assert
        // against that same value so the test is platform-independent.
        assertTrue(s.contains("program=\"${bin.absolutePath}\""))
    }

    @Test
    fun isIdempotentDeleteThenAdd() {
        val s = FirewallRuleScript.build(listOf(File("C:\\Games\\javaw.exe")), marker)
        val del = s.indexOf("advfirewall firewall delete rule")
        val add = s.indexOf("advfirewall firewall add rule")
        assertTrue("delete must precede add", del in 0 until add)
    }

    @Test
    fun quotesCyrillicAndSpacedPaths() {
        val bin = File("C:\\Пользователи\\Конст Games\\javaw.exe")
        val s = FirewallRuleScript.build(listOf(bin), marker)
        // Cyrillic + spaces must survive intact inside the quotes.
        assertTrue(s.contains("program=\"${bin.absolutePath}\""))
        assertTrue(bin.absolutePath.contains("Конст Games"))
    }

    @Test
    fun writesMarkerLast() {
        val s = FirewallRuleScript.build(listOf(File("C:\\a\\javaw.exe")), marker)
        assertTrue(s.contains(marker.absolutePath))
        assertTrue("marker write must come after the last add rule", s.lastIndexOf(marker.absolutePath) > s.lastIndexOf("add rule"))
    }

    @Test
    fun oneRulePerBinaryDistinctNames() {
        val s = FirewallRuleScript.build(listOf(File("C:\\a\\java.exe"), File("C:\\a\\javaw.exe")), marker)
        assertTrue(s.contains("TechnoparkLauncher 1"))
        assertTrue(s.contains("TechnoparkLauncher 2"))
    }
}
