package ru.lionzxy.tplauncher.view.settings.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import tornadofx.View

class CloseListener(val view: View) : EventHandler<MouseEvent> {
    override fun handle(p0: MouseEvent?) {
        view.close()
    }
}