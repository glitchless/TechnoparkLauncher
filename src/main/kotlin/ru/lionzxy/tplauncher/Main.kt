package ru.lionzxy.tplauncher

import javafx.application.Application
import ru.lionzxy.tplauncher.utils.configureHttpUserAgent

fun main(vararg args: String) {
    configureHttpUserAgent()
    Application.launch(MainApplication::class.java, *args)
}
