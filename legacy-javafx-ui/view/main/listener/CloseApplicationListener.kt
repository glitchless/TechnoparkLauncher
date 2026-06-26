package ru.lionzxy.tplauncher.view.main.listener

import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent

class CloseApplicationListener : EventHandler<MouseEvent> {
    override fun handle(p0: MouseEvent?) {
        Platform.exit()
    }
}