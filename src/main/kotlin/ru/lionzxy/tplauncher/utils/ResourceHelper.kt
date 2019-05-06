package ru.lionzxy.tplauncher.utils

import javafx.scene.text.Font
import java.net.URL

object ResourceHelper {

    fun getResource(path: String): URL {
        return javaClass.getResource("/$path")
    }

    fun getFont(path: String, size: Double): Font {
        return Font.loadFont(getResource(path).openStream(), size)
    }
}