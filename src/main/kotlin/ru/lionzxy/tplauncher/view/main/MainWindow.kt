package ru.lionzxy.tplauncher.view.main

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import ru.lionzxy.tplauncher.utils.*
import ru.lionzxy.tplauncher.utils.Constants.DEFAULT_MARGIN
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.activated
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.progressbox
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import tornadofx.*
import tornadofx.Stylesheet.Companion.disabled

class MainWindow : View(), IProgressMonitor {
    private var loginCompleteArea: HBox by singleAssign()
    private var loginField: Field by singleAssign()
    private var passwordField: Field by singleAssign()
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

        form {
            padding = Insets(DEFAULT_MARGIN, DEFAULT_MARGIN, 0.0, 23.0)
            fieldset() {
                alignment = Pos.CENTER
                gridpane {
                    loginCompleteArea = hbox {
                        hide()
                        minWidth = 302.0
                        gridpaneConstraints {
                            rowSpan = 2
                        }
                    }
                    row {
                        loginField = field("Логин") {
                            textfield()
                        }
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
                            recursiveApplyToChild { addClass(disabled) }
                        }
                    }
                    row {
                        passwordField = field("Пароль") {
                            passwordfield()
                        }

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
            padding = Insets(0.0, DEFAULT_MARGIN, DEFAULT_MARGIN, 23.0)
            addClass(activated)
        }

        button("Войти в игру") {
            useMaxWidth = true
            minHeight = 36.0
            vboxConstraints {
                marginLeft = DEFAULT_MARGIN
                marginBottom = DEFAULT_MARGIN
                marginRight = DEFAULT_MARGIN
            }
            action {
                loginField.hide()
                passwordField.hide()
                loginCompleteArea.show()
            }
        }

        vbox(DEFAULT_MARGIN) {
            padding = Insets(DEFAULT_MARGIN)
            alignment = Pos.CENTER
            addClass(progressbox)
            label("Введите почту и пароль")
            progressbar {
                progress = 0.0
                useMaxWidth = true
                style {
                    accentColor = Constants.accentColor
                }
            }
            recursiveApplyToChild { addClass(disabled) }
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