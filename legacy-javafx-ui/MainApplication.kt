package ru.lionzxy.tplauncher

import com.sun.javafx.application.PlatformImpl
import de.codecentric.centerdevice.javafxsvg.SvgImageLoaderFactory
import io.sentry.Sentry
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import io.sentry.event.EventBuilder
import javafx.stage.Stage
import javafx.stage.StageStyle
import ru.lionzxy.tplauncher.utils.LogoUtils
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet
import ru.lionzxy.tplauncher.view.main.MainWindow
import tornadofx.App
import tornadofx.reloadStylesheetsOnFocus
import java.lang.RuntimeException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

val ASYNC_TASK_EXECUTOR: ExecutorService = Executors.newCachedThreadPool()

val SENTRY: SentryClient =
    SentryClientFactory.sentryClient("https://cd312e191fbd44b49c6cc526bb91817c@sentry.team.glitchless.ru/18").apply {
        serverName = BuildConfig.NAME
        tags = HashMap(tags).apply {
            this["version"] = BuildConfig.VERSION
        }
    }

class MainApplication : App(MainWindow::class, GlobalStylesheet::class), PlatformImpl.FinishListener {
    init {
        Sentry.setStoredClient(SENTRY)
        PlatformImpl.addListener(this)
        SvgImageLoaderFactory.install()
        reloadStylesheetsOnFocus()
    }

    override fun start(stage: Stage) {
        stage.initStyle(StageStyle.UNDECORATED)
        LogoUtils.prepareLogo()
        LogoUtils.setLogo(stage)
        super.start(stage)
    }

    override fun exitCalled() {
        ASYNC_TASK_EXECUTOR.shutdownNow()
    }

    override fun idle(implicitExit: Boolean) {
        println("Idle. implicitExit: $implicitExit")
    }
}