package ru.lionzxy.tplauncher.view.main

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.ResourceHelper
import ru.lionzxy.tplauncher.utils.runOnUi
import ru.lionzxy.tplauncher.utils.svgview
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import tornadofx.*

class MainWindow : View(), IProgressMonitor {
    //private lateinit var status: Label
    //private lateinit var progressBar: ProgressBar
    //private lateinit var loginPasswordBox: HBox
    //private lateinit var downloadButton: Button
    private var maxProgressBar = 1
    private var currentProgress = 1
    private val controller = MainController(this)

    override val root = vbox {
        label("games.glitchless.ru") {
            onMouseClicked = OpenSiteListener("https://games.glitchless.ru")
            padding = Insets(11.5, 0.0, 0.0, 23.0)
            style {
                font = ResourceHelper.getFont("Gugi-Regular.ttf", 30.0)
                textFill = Constants.accentColor
            }
        }

        hbox {
            padding = Insets(0.0, 0.0, 0.0, 16.0)
            form {
                fieldset() {
                    field("Логин") { textfield() }
                    field("Пароль") { passwordfield() }
                }
            }
            form {
                fieldset() {
                    field("Сервер") {
                        combobox<String>() {
                            minWidth = 129.0
                            items = FXCollections.observableArrayList("Test")
                            selectionModel.select(0)
                        }
                    }

                    hbox {
                        svgview("cogs-solid") {
                            fitWidth = 38.0
                            fitHeight = 38.0
                        }
                        label("Настройки запуска") {
                            alignment = Pos.CENTER
                        }
                    }
                }
            }
        }
    }

    fun closeApplication() {
        runOnUi {
            Platform.exit()
        }
    }

    fun showDownloadAndPlayButton(visible: Boolean) {
        /* runOnUi {
             if (visible) {
                 downloadButton.show()
             } else {
                 downloadButton.hide()
             }
         }*/
    }

    fun showProgress(visible: Boolean) {
        /* runOnUi {
             if (visible) {
                 progressBar.show()
                 status.show()
             } else {
                 progressBar.hide()
                 status.hide()
             }
         }*/
    }

    fun showLoginPassword(visible: Boolean) {
        /*runOnUi {
            if (visible) {
                loginPasswordBox.show()
            } else {
                loginPasswordBox.hide()
            }
        }*/
    }

    override fun setMax(len: Int) {
        maxProgressBar = len
    }

    override fun setProgress(progress: Int) {
        /*runOnUi {
            if (progress == -1) {
                progressBar.progress = INDETERMINATE_PROGRESS
                return@runOnUi
            }
            currentProgress = progress
            progressBar.progress = progress.toDouble() / maxProgressBar.toDouble()
        }*/
    }

    override fun setStatus(status: String?) {
        /*runOnUi {
            this.status.show()
            this.status.text = status
        }*/
    }

    override fun incrementProgress(amount: Int) {
        setProgress(currentProgress + amount)
    }
}