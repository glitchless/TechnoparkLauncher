package ru.lionzxy.tplauncher.utils

import com.sun.management.OperatingSystemMXBean
import ru.lionzxy.tplauncher.log.Logger
import java.lang.management.ManagementFactory


object SystemMemoryHelper {
    /**
     * @return total ram size in bytes
     */
    fun getSystemTotalMemory(): Long? {
        return try {
            val bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
            bean.totalMemorySize.takeIf { it > 0 }
        } catch (e: Exception) {
            Logger.w("Memory", "Failed to query system memory", e)
            null
        }
    }
}
