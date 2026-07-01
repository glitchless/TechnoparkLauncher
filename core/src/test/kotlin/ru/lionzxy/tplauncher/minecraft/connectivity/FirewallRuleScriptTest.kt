package ru.lionzxy.tplauncher.minecraft.connectivity

import org.junit.Assert.assertEquals
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
    fun switchesConsoleToUtf8BeforeAnyPath() {
        // cmd.exe parses batch files in the OEM codepage; without chcp 65001 a Cyrillic path
        // written as UTF-8 is mojibaked and the rules point at nonexistent files.
        val s = FirewallRuleScript.build(listOf(File("C:\\Пользователи\\Конст Games\\javaw.exe")), marker)
        val chcp = s.indexOf("chcp 65001")
        val firstNetsh = s.indexOf("netsh")
        assertTrue("chcp 65001 must precede all netsh lines", chcp in 0 until firstNetsh)
        // Everything before chcp must be plain ASCII (still parsed in the OEM codepage).
        assertTrue(s.substring(0, chcp).all { it.code < 128 })
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
    fun allRulesShareOneNameSoOneDeleteCleansStaleRules() {
        // A single shared rule name means the one delete line removes ALL rules from any previous
        // run — including a run that had MORE binaries (a positional "name N" scheme leaks those).
        val s = FirewallRuleScript.build(listOf(File("C:\\a\\java.exe"), File("C:\\a\\javaw.exe")), marker)
        assertEquals(1, Regex("delete rule").findAll(s).count())
        assertEquals(2, Regex("add rule name=\"TechnoparkLauncher\"").findAll(s).count())
        assertTrue(s.contains("delete rule name=\"TechnoparkLauncher\""))
    }
}
