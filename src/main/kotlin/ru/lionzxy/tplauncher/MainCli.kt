package ru.lionzxy.tplauncher

import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.prepare.ComposePrepare
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.configureHttpUserAgent
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import kotlin.system.exitProcess

/**
 * Headless entry point that runs the same flow as the UI (MainController#onLogin + onGameStart):
 * authenticate, then prepare and launch Minecraft. Unlike the UI it prints the full exception
 * chain, so the real cause behind "Failed to authenticate..." (a 403 vs an SSL/timeout IOException)
 * is visible instead of being collapsed into one generic message.
 *
 * Usage: MainCliKt <email> <password> [--no-launch]
 *        (email/password also read from TPL_EMAIL / TPL_PASSWORD env vars)
 */
private class ConsoleProgressMonitor : IProgressMonitor {
    private var max = 1
    override fun setMax(len: Int) {
        max = len
    }

    override fun setProgress(progress: Int) {
        if (max > 0) System.out.printf("\r[progress] %.1f%%   ", progress.toFloat() / max * 100)
    }

    override fun setStatus(status: String?) {
        println("\n[status] $status")
    }

    override fun incrementProgress(amount: Int) {}
}

private fun dumpChain(t: Throwable?, depth: Int) {
    if (t == null) return
    val indent = "  ".repeat(depth)
    System.err.println("$indent- ${t.javaClass.name}: ${t.message}")
    if (t is YDServiceAuthenticationException) {
        t.reason?.let { System.err.println("$indent    reason=$it") }
        dumpChain(t.thrown, depth + 1)
    }
    if (t.cause != null && t.cause !== t) {
        dumpChain(t.cause, depth + 1)
    }
}

fun main(args: Array<String>) {
    configureHttpUserAgent()

    val positional = args.filterNot { it.startsWith("--") }
    val email = positional.getOrNull(0) ?: System.getenv("TPL_EMAIL")
    val password = positional.getOrNull(1) ?: System.getenv("TPL_PASSWORD")
    val doLaunch = !args.contains("--no-launch")

    if (email.isNullOrBlank() || password.isNullOrBlank()) {
        System.err.println("Usage: MainCliKt <email> <password> [--no-launch]")
        System.err.println("       (or set TPL_EMAIL / TPL_PASSWORD)")
        exitProcess(2)
    }

    val modpack = ConfigHelper.config.currentModpack
    println("Modpack:    $modpack")
    println("MC dir:     ${ConfigHelper.getMinecraftDirectory(modpack)}")
    println("Auth host:  ru.lionzxy.tplauncher.minecraft (custom Yggdrasil)")

    val context = MinecraftContext(ConsoleProgressMonitor(), modpack, MinecraftAccountManager(modpack))

    // ---- LOGIN (mirrors MainController.onLogin) ----
    println("\n=== AUTH === logging in as $email ...")
    try {
        context.minecraftAccountManager.login(email, password)
    } catch (e: YDServiceAuthenticationException) {
        System.err.println("LOGIN FAILED: ${e.javaClass.simpleName}: ${e.message}")
        System.err.println("--- exception chain (getThrown + getCause) ---")
        dumpChain(e, 0)
        exitProcess(1)
    }
    val session = context.minecraftAccountManager.session
    println("LOGIN OK: username=${session?.username} uuid=${session?.uuid}")

    if (!doLaunch) {
        println("--no-launch given; stopping after auth.")
        return
    }

    // ---- PREPARE + LAUNCH (mirrors MainController.onGameStart; logo step is UI-only, skipped) ----
    println("\n=== PREPARE === downloading / syncing modpack ...")
    try {
        ComposePrepare().prepareMinecraft(context)
        println("\n=== LAUNCH === starting Minecraft ...")
        context.launch()
        println("Minecraft process started (see mcout.log / mcerr.log in the game dir).")
    } catch (e: Exception) {
        System.err.println("PREPARE/LAUNCH FAILED: ${e.javaClass.name}: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
