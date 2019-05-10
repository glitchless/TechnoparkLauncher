package ru.lionzxy.tplauncher.view.settings.listener

import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.utils.ConfigHelper

class LogOutListener : EventHandler<MouseEvent> {
    override fun handle(eventClick: MouseEvent?) {
        ConfigHelper.writeToConfig {
            profile = null
        }

        Platform.exit()
    }

}