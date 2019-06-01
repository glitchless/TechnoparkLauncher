package ru.lionzxy.tplauncher

import javafx.application.Application
import javafx.application.Platform
import ru.lionzxy.tplauncher.utils.LogoUtils
import java.util.concurrent.Executors

fun main(vararg args: String) {
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