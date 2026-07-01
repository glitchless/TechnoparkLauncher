package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

/**
 * Pure assembly of firewall allow-list candidates: the current process image plus `java.exe`/`javaw.exe`
 * under the launcher's own runtime ([javaHome]) and the active modpack's bundled JRE ([bundledJreBinDir]).
 * Deduped by path (a [LinkedHashSet] preserves order) and filtered to those [exists] accepts. Any input
 * may be null (e.g. process image unresolved, no bundled JRE); nulls are skipped.
 */
fun assembleBinaryCandidates(
    processImage: File?,
    javaHome: File?,
    bundledJreBinDir: File?,
    exists: (File) -> Boolean,
): List<File> {
    val out = LinkedHashSet<File>()
    processImage?.let { out.add(it) }
    listOfNotNull(javaHome?.let { File(it, "bin") }, bundledJreBinDir).forEach { dir ->
        out.add(File(dir, "java.exe"))
        out.add(File(dir, "javaw.exe"))
    }
    return out.filter(exists)
}
