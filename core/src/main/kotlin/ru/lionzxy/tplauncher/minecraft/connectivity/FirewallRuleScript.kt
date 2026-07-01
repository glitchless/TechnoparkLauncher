package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/**
 * Builds a batch (`.cmd`) body that allow-lists each binary for OUTBOUND traffic in Windows Firewall.
 *
 * All rules share ONE name ([ruleName]): netsh allows several rules under the same name, and
 * `delete rule name=...` removes ALL of them — so a single delete line both makes re-runs idempotent
 * and cleans up stale rules from a previous run with a different binary count (positional
 * "name N" schemes leak rules when the count shrinks).
 *
 * cmd.exe parses batch files in the console OEM codepage (e.g. CP866 on Russian Windows), NOT the
 * UTF-8 that [File.writeText] produces — so `chcp 65001` on the second (pure-ASCII) line switches
 * the console to UTF-8 before any interpolated path is parsed, letting Cyrillic/space-containing
 * program paths survive intact; they are also always quoted. Rule NAMES are our own constant text
 * (never interpolated from untrusted input). The last line touches [markerFile] so the caller can
 * confirm the elevated script ran to completion.
 */
object FirewallRuleScript {
    fun build(binaries: List<File>, markerFile: File, ruleName: String = "TechnoparkLauncher"): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        // Written as UTF-8 (no BOM) by the caller; from here on cmd parses lines as UTF-8.
        sb.appendLine("chcp 65001 >nul")
        // One delete for the shared name removes every rule from any previous run, whatever its count.
        sb.appendLine("netsh advfirewall firewall delete rule name=\"$ruleName\" >nul 2>&1")
        binaries.forEach { bin ->
            sb.appendLine(
                "netsh advfirewall firewall add rule name=\"$ruleName\" dir=out action=allow " +
                    "program=\"${bin.absolutePath}\" enable=yes profile=any",
            )
        }
        // Marker written last: its presence == the script completed all rules.
        sb.appendLine("echo done> \"${markerFile.absolutePath}\"")
        return sb.toString()
    }
}
