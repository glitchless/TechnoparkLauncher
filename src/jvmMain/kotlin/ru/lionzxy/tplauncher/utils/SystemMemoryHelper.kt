package ru.lionzxy.tplauncher.utils

import java.io.BufferedReader
import java.io.InputStreamReader


object SystemMemoryHelper {
    /**
     * @return total ram size in bytes
     */
    fun getSystemTotalMemory(): Long? {
        return when (OperatingSystem.current()) {
            OperatingSystem.MACOS -> getSystemTotalMemoryOSX()
            OperatingSystem.WINDOWS -> getSystemTotalMemoryWindows()
            OperatingSystem.LINUX -> getSystemTotalMemoryLinux()
        }
    }

    private fun getSystemTotalMemoryOSX(): Long? {
        val result = execute("sysctl", "hw.memsize") ?: return null
        val regex = "hw\\.memsize: (\\d+)".toRegex()
        val bytes = regex.find(result)?.groupValues?.lastOrNull() ?: return null
        return bytes.trim().toLongOrNull()
    }

    private fun getSystemTotalMemoryWindows(): Long? {
        val result = execute("wmic", "OS", "get", "TotalVisibleMemorySize") ?: return null
        val regex = "TotalVisibleMemorySize[^\\d]*(\\d+)".toRegex()
        val bytes = regex.find(result)?.groupValues?.lastOrNull() ?: return null
        val kbytes = bytes.trim().toLongOrNull() ?: return null
        return kbytes * 1000
    }

    private fun getSystemTotalMemoryLinux(): Long? {
        val result = execute("cat", "/proc/meminfo") ?: return null
        val regex = "MemTotal:[^\\d]*(\\d+) kB".toRegex()
        val bytes = regex.find(result)?.groupValues?.lastOrNull() ?: return null
        val kbytes = bytes.trim().toLongOrNull() ?: return null
        return kbytes * 1000
    }

    private fun execute(vararg args: String): String? {
        val sb = StringBuilder()

        val processBuilder = ProcessBuilder()
        processBuilder.command(args.toList())
        try {
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String? = null
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return sb.toString()
    }
}
