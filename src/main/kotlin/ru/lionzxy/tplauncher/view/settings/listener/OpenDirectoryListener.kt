package ru.lionzxy.tplauncher.view.settings.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.utils.ConfigHelper
import java.awt.Desktop

class OpenDirectoryListener : EventHandler<MouseEvent> {
    override fun handle(clickEvent: MouseEvent?) {
        Desktop.getDesktop().open(ConfigHelper.getDefaultDirectory())
    }

}