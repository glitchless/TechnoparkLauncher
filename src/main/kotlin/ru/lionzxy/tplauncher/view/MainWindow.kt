package ru.lionzxy.tplauncher.view

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.image.Image
import ru.lionzxy.tplauncher.utils.LocalizationHelper
import ru.lionzxy.tplauncher.utils.ResourceHelper
import tornadofx.*

class MainWindow : View() {
    override val root = vbox {
        imageview(Image(ResourceHelper.getResource("tplogo.png").toExternalForm())) {
            vboxConstraints {
                margin = Insets(20.0)
            }
            alignment = Pos.CENTER
        }
        hbox {
            vboxConstraints {
                margin = Insets(0.0, 5.0, 0.0, 5.0)
            }
            textfield {
                promptText = LocalizationHelper.getString("login_enter_login", "Enter login")
            }
            textfield {
                promptText = LocalizationHelper.getString("login_enter_password", "Enter password")
            }
            button(LocalizationHelper.getString("login_btn", "Login")) {

            }
        }
        progressbar {
            vboxConstraints {
                margin = Insets(0.0, 5.0, 0.0, 5.0)
            }
            useMaxWidth = true
        }
    }
}