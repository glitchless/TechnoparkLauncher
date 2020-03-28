package ru.lionzxy.tplauncher.prepare.processing

import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.IPrepareTask
import java.io.File

private const val OPTIONS_FILE = "options.txt"
private val OPTIONS_DIFF_REGEX = "options\\.([0-9]+)\\.diff".toRegex()

class MergeOptionsProcessing : IPrepareTask {
    @Throws(Exception::class)
    override fun prepareMinecraft(minecraft: MinecraftContext) {
        minecraft.progressMonitor.setStatus("Применение текущих опций")
        minecraft.progressMonitor.setProgress(-1)
        val map = HashMap(readCurrentOptions(minecraft))
        map.putAll(readChangesOptions(minecraft))
        if (saveOptions(minecraft, map)) {
            minecraft.getDirectory().listFiles()?.filter {
                OPTIONS_DIFF_REGEX.matches(it.name)
            }?.forEach { it.delete() }
        }
    }

    private fun readCurrentOptions(minecraft: MinecraftContext): Map<String, String> {
        val optionsFile = File(minecraft.getDirectory(), OPTIONS_FILE)
        if (!optionsFile.exists()) {
            return emptyMap()
        }

        return optionsFile.readLines()
            .map { it.split(":") }
            .filter { it.size > 1 }
            .map { it[0] to it[1] }
            .toMap()
    }

    private fun readChangesOptions(minecraft: MinecraftContext): Map<String, String> {
        val optionsDiffFiles = minecraft.getDirectory().listFiles()?.filter {
            OPTIONS_DIFF_REGEX.matches(it.name)
        }?.sortedBy { OPTIONS_DIFF_REGEX.find(it.name)?.groupValues?.getOrNull(1)?.toLong() }
        if (optionsDiffFiles == null || optionsDiffFiles.isEmpty()) {
            return emptyMap()
        }

        val diffMap = HashMap<String, String>()
        optionsDiffFiles.forEach { file ->
            diffMap.putAll(file.readLines()
                .map { it.trim(' ', '\n').split(":") }
                .filter { it.size > 1 }
                .map { it[0] to it[1] }
                .toMap())
        }
        return diffMap
    }

    private fun saveOptions(minecraft: MinecraftContext, options: Map<String, String>): Boolean {
        val optionsFile = File(minecraft.getDirectory(), OPTIONS_FILE)
        if (optionsFile.exists()) {
            optionsFile.delete()
        }
        if (!optionsFile.createNewFile()) {
            return false
        }

        val newContent = options.map { "${it.key}:${it.value}" }.joinToString("\n")
        optionsFile.writeText(newContent)
        return true
    }
}
