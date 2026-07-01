package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/**
 * Builds a batch (`.cmd`) body that allow-lists each binary for OUTBOUND traffic in Windows Firewall.
 *
 * Idempotent: deletes any prior same-named rule before adding, so re-running never duplicates rules.
 * Program paths are wrapped in double quotes (safe for spaces and Cyrillic). The last line touches
 * [markerFile] so the caller can confirm the elevated script ran to completion. Rule NAMES are our own
 * constant text (never interpolated from untrusted input); only OS-resolved program paths are
 * interpolated, and they are always quoted.
 */
object FirewallRuleScript {
    fun build(binaries: List<File>, markerFile: File, ruleNamePrefix: String = "TechnoparkLauncher"): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        binaries.forEachIndexed { i, bin ->
            val name = "$ruleNamePrefix ${i + 1}"
            sb.appendLine("netsh advfirewall firewall delete rule name=\"$name\" >nul 2>&1")
            sb.appendLine(
                "netsh advfirewall firewall add rule name=\"$name\" dir=out action=allow " +
                    "program=\"${bin.absolutePath}\" enable=yes profile=any",
            )
        }
        // Marker written last: its presence == the script completed all rules.
        sb.appendLine("echo done> \"${markerFile.absolutePath}\"")
        return sb.toString()
    }
}
