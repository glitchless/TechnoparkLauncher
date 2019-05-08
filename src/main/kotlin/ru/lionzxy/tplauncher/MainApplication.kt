package ru.lionzxy.tplauncher

import de.codecentric.centerdevice.javafxsvg.SvgImageLoaderFactory
import javafx.stage.Stage
import javafx.stage.StageStyle
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet
import ru.lionzxy.tplauncher.view.main.MainWindow
import tornadofx.App
import tornadofx.reloadStylesheetsOnFocus

class MainApplication : App(MainWindow::class, GlobalStylesheet::class) {
    init {
        SvgImageLoaderFactory.install()
        reloadStylesheetsOnFocus()
    }

    override fun start(stage: Stage) {
        stage.initStyle(StageStyle.UNDECORATED)
        super.start(stage)
    }
}