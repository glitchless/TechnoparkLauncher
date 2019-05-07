package ru.lionzxy.tplauncher.view.main

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.layout.Priority
import ru.lionzxy.tplauncher.utils.*
import ru.lionzxy.tplauncher.utils.Constants.DEFAULT_MARGIN
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
        padding = Insets(0.0, DEFAULT_MARGIN, 0.0, DEFAULT_MARGIN)
        label("games.glitchless.ru") {
            onMouseClicked = OpenSiteListener("https://games.glitchless.ru")
            padding = Insets(11.5, -DEFAULT_MARGIN, 0.0, 23.0 - DEFAULT_MARGIN)
            style {
                font = ResourceHelper.getFont("Gugi-Regular.ttf", 30.0)
                textFill = Constants.accentColor
            }
        }

        form {
            fieldset() {
                alignment = Pos.CENTER
                gridpane {
                    row {
                        field("Логин") { textfield() }
                        field("Сервер") {
                            gridpaneConstraints {
                                marginLeft = DEFAULT_MARGIN * 2
                            }
                            combobox<String> {
                                hgrow = Priority.ALWAYS
                                maxWidth = Double.MAX_VALUE
                                items = FXCollections.observableArrayList("Test")
                                selectionModel.select(0)
                                isDisable = true
                            }
                            recursiveApplyToChild { addPseudoClass("disabled") }
                        }
                    }
                    row {
                        field("Пароль") { passwordfield() }

                        hbox {
                            gridpaneConstraints {
                                marginLeft = DEFAULT_MARGIN * 2
                            }
                            alignment = Pos.CENTER
                            svgview("cogs-solid") {
                                fitWidth = 34.0
                                fitHeight = 34.0
                            }
                            label("Настройки запуска") {
                                padding = Insets(0.0, 0.0, 0.0, DEFAULT_MARGIN)

                            }
                        }
                    }
                }
            }
        }
        label("Регистрация на сайте") {
            onMouseClicked = OpenSiteListener("https://games.glitchless.ru/register/")
            padding = Insets(-DEFAULT_MARGIN, 0.0, DEFAULT_MARGIN, 23.0 - DEFAULT_MARGIN)
            addPseudoClass("activated")
        }
        button("Войти в игру") {
            useMaxWidth = true
            minHeight = 36.0
        }

        hbox {
            padding = Insets(DEFAULT_MARGIN, 0.0, 0.0, 0.0)
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