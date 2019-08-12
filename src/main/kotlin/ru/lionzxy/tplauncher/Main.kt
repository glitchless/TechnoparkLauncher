package ru.lionzxy.tplauncher

import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import javafx.application.Application
import javafx.application.Platform
import ru.lionzxy.tplauncher.utils.LogoUtils
import java.util.concurrent.Executors

val SENTRY = SentryClientFactory.sentryClient("https://cd312e191fbd44b49c6cc526bb91817c@sentry.team.glitchless.ru/18")

fun main(vararg args: String) {
    SENTRY.serverName = "Launcher"
    SENTRY.tags = HashMap(SENTRY.tags).apply {
        this["version"] = BuildConfig.VERSION
    }


    LogoUtils.prepareLogo()
    LogoUtils.setLogo()
    Application.launch(MainApplication::class.java, *args)
}

object Main {
    val asyncTaskExecutor = Executors.newCachedThreadPool()

    fun closeApplication() {
        asyncTaskExecutor.shutdownNow()
        Platform.exit()
    }
}