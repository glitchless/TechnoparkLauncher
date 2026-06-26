package ru.lionzxy.tplauncher.view.main.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import javafx.stage.StageStyle
import ru.lionzxy.tplauncher.view.settings.SettingsWindow
import tornadofx.View

class OpenSettingsListener : EventHandler<MouseEvent> {
    private val settingsWindow: View by lazy {
        SettingsWindow()
    }

    override fun handle(eventClick: MouseEvent?) {
        settingsWindow.openWindow(StageStyle.UNDECORATED)
    }

}