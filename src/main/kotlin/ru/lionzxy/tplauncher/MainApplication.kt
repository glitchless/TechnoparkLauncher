package ru.lionzxy.tplauncher

import ru.lionzxy.tplauncher.view.MainWindow
import tornadofx.App

fun main() {
    val app = MainApplication()
    app.launchApp()
}

class MainApplication : App() {
    override val primaryView = MainWindow::class

    fun launchApp() {
        launch()
    }
}