package ru.lionzxy.tplauncher

import javafx.application.Application
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.deleteDirectoryRecursionJava6

fun main(vararg args: String) {
    ConfigHelper.getTemporaryDirectory().deleteDirectoryRecursionJava6()
    Application.launch(MainApplication::class.java, *args)
}
