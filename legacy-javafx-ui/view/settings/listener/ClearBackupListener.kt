package ru.lionzxy.tplauncher.view.settings.listener

import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6
import ru.lionzxy.tplauncher.utils.folderSize
import ru.lionzxy.tplauncher.utils.humanReadableByteCountBin

class ClearBackupListener(var label: Label) : EventHandler<MouseEvent> {
    companion object {
        fun getBackupClearText(): String {
            return "Очистить папку с бекапом (${ConfigHelper.getBackupFolder().folderSize().humanReadableByteCountBin()})"
        }
    }

    override fun handle(event: MouseEvent?) {
        ConfigHelper.getBackupFolder().deleteDirectoryRecursionJava6()
        label.text = getBackupClearText()
    }

}
