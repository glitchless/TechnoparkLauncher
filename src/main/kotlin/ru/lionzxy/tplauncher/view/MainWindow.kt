package ru.lionzxy.tplauncher.view

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
import javafx.scene.image.Image
import javafx.scene.layout.HBox
import ru.lionzxy.tplauncher.utils.LocalizationHelper
import ru.lionzxy.tplauncher.utils.ResourceHelper
import ru.lionzxy.tplauncher.utils.runOnUi
import ru.lionzxy.tplauncher.view.controllers.MainController
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import tornadofx.*

class MainWindow : View(), IProgressMonitor {
    private lateinit var status: Label
    private lateinit var progressBar: ProgressBar
    private lateinit var loginPasswordBox: HBox
    private lateinit var downloadButton: Button
    private var maxProgressBar = 1
    private var currentProgress = 1
    private val controller = MainController(this)

    override val root = vbox {
        vboxConstraints {
            padding = Insets(20.0)
        }
        imageview(Image(ResourceHelper.getResource("tplogo.png").toExternalForm())) {
            vboxConstraints {
                margin = Insets(0.0, 20.0, 20.0, 20.0)
            }
            alignment = Pos.CENTER
        }
        loginPasswordBox = hbox {
            vboxConstraints {
                margin = Insets(0.0, 5.0, 0.0, 5.0)
            }
            val login = textfield {
                promptText = LocalizationHelper.getString("login_enter_login")
            }
            val password = textfield {
                promptText = LocalizationHelper.getString("login_enter_password")
            }
            button(LocalizationHelper.getString("login_btn")) {
                action {
                    controller.onLogin(login.text, password.text)
                }
            }
        }
        downloadButton = button(LocalizationHelper.getString("login_download_button")) {
            action { controller.downloadAndLaunch() }
            alignment = Pos.CENTER
            vboxConstraints {
                margin = Insets(0.0, 5.0, 0.0, 5.0)
            }
        }
        progressBar = progressbar {
            vboxConstraints {
                margin = Insets(0.0, 5.0, 0.0, 5.0)
            }
            useMaxWidth = true
        }
        status = label(LocalizationHelper.getString("login_status")) {
            alignment = Pos.CENTER
        }
    }

    fun closeApplication() {
        runOnUi {
            Platform.exit()
        }
    }

    fun showDownloadAndPlayButton(visible: Boolean) {
        runOnUi {
            if (visible) {
                downloadButton.show()
            } else {
                downloadButton.hide()
            }
        }
    }

    fun showProgress(visible: Boolean) {
        runOnUi {
            if (visible) {
                progressBar.show()
                status.show()
            } else {
                progressBar.hide()
                status.hide()
            }
        }
    }

    fun showLoginPassword(visible: Boolean) {
        runOnUi {
            if (visible) {
                loginPasswordBox.show()
            } else {
                loginPasswordBox.hide()
            }
        }
    }

    override fun setMax(len: Int) {
        maxProgressBar = len
    }

    override fun setProgress(progress: Int) {
        runOnUi {
            if (progress == -1) {
                progressBar.progress = INDETERMINATE_PROGRESS
                return@runOnUi
            }
            currentProgress = progress
            progressBar.progress = progress.toDouble() / maxProgressBar.toDouble()
        }
    }

    override fun setStatus(status: String?) {
        runOnUi {
            this.status.show()
            this.status.text = status
        }
    }

    override fun incrementProgress(amount: Int) {
        setProgress(currentProgress + amount)
    }
}