package ru.lionzxy.tplauncher.view.main.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.Main

class CloseApplicationListener : EventHandler<MouseEvent> {
    override fun handle(p0: MouseEvent?) {
        Main.closeApplication()
    }
}