package ru.lionzxy.tplauncher.utils


enum class Arch {
    X86,
    ARM;

    companion object {
        fun current(): Arch {
            val arch = System.getProperty("os.arch")
            return when {
                arch.startsWith("x86", ignoreCase = true) -> X86
                arch.startsWith("aarch64", ignoreCase = true) -> ARM
                else -> error("Arch \"$arch\" not supported")
            }
        }
    }
}

enum class OperatingSystem {
    WINDOWS,
    LINUX,
    MACOS;

    companion object {
        fun current(): OperatingSystem {
            val system = System.getProperty("os.name")
            return when {
                system.contains("Windows", ignoreCase = true) -> WINDOWS
                system.contains("Linux", ignoreCase = true)
                        or system.contains("unix", ignoreCase = true) -> LINUX

                system.contains("Mac OS", ignoreCase = true) -> MACOS
                else -> error("Operating system \"$system\" not supported")
            }
        }
    }
}