package ru.lionzxy.tplauncher.view.settings.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.Main
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6

class DeleteAllListener : EventHandler<MouseEvent> {
    override fun handle(eventClick: MouseEvent?) {
        val defaultDir = ConfigHelper.getDefaultDirectory()
        defaultDir.listFiles().forEach {
            if (it.absolutePath == ConfigHelper.getJREPathFile().absolutePath) {
                return@forEach
            }

            if (it.absolutePath == ConfigHelper.getJavaDirectory().absolutePath) {
                return@forEach
            }
            it.deleteDirectoryRecursionJava6()
        }
        Main.closeApplication()
    }

}