package ru.lionzxy.tplauncher.minecraft.connectivity

import java.io.File

sealed interface ElevationResult {
    /** The elevated script ran. NOTE: this is NOT "connectivity fixed" — the probe decides that. */
    object Success : ElevationResult

    /** The user declined the UAC prompt. */
    object UserCancelled : ElevationResult

    data class Failed(val reason: String) : ElevationResult
}

/** Runs a script with administrator privileges (UAC). */
interface ElevatedRunner {
    fun runElevated(scriptFile: File): ElevationResult
}

/** Resolves the executable images whose outbound sockets should be allow-listed. Empty on non-Windows. */
interface BinaryResolver {
    fun resolve(): List<File>
}
