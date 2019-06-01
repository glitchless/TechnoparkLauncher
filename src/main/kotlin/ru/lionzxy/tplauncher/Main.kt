package ru.lionzxy.tplauncher

import javafx.application.Application
import ru.lionzxy.tplauncher.utils.LogoUtils

fun main(vararg args: String) {
    LogoUtils.prepareLogo()
    LogoUtils.setLogo()
    Application.launch(MainApplication::class.java, *args)
}
