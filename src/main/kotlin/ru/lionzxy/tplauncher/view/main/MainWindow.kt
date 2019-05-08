package ru.lionzxy.tplauncher.view.main

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.Constants.DEFAULT_MARGIN
import ru.lionzxy.tplauncher.utils.recursiveApplyToChild
import ru.lionzxy.tplauncher.utils.runOnUi
import ru.lionzxy.tplauncher.utils.svgview
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.activated
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.progressbox
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.successLogin
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.titleStyle
import ru.lionzxy.tplauncher.view.main.states.BaseState
import ru.lionzxy.tplauncher.view.main.states.InitialState
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import tornadofx.*
import tornadofx.Stylesheet.Companion.disabled

class MainWindow : View(), IProgressMonitor {
    private var loginCompleteArea: HBox by singleAssign()
    private var loginField: Field by singleAssign()
    private var passwordField: Field by singleAssign()
    private var titleLabel: Label by singleAssign()
    private var loginButton: Button by singleAssign()
    private var progressText: Label by singleAssign()
    private var progressBar: ProgressBar by singleAssign()
    private var successLoginText: Label by singleAssign()
    private var registerLabel: Label by singleAssign()
    private var currentBaseState: BaseState = BaseState()

    override val root = vbox {
        println(isResizable)
        titleLabel = label("games.glitchless.ru") {
            onMouseClicked = OpenSiteListener("https://games.glitchless.ru")
            padding = Insets(11.5, 0.0, 0.0, 23.0)
            addClass(titleStyle)
        }

        form {
            padding = Insets(DEFAULT_MARGIN, DEFAULT_MARGIN, 0.0, 23.0)
            fieldset() {
                alignment = Pos.CENTER
                gridpane {
                    loginCompleteArea = hbox {
                        alignment = Pos.CENTER_LEFT
                        minWidth = 302.0
                        minHeight = 96.0
                        gridpaneConstraints {
                            rowSpan = 2
                        }
                        stackpane {
                            circle {
                                radius = 42.0
                                fill = Constants.backgroundCircleColor
                            }
                            svgview("check-solid") {
                                fitWidth = 42.0
                                fitHeight = 42.0
                            }
                        }
                        vbox {
                            hboxConstraints {
                                marginLeft = DEFAULT_MARGIN
                            }
                            alignment = Pos.CENTER
                            successLoginText = label("st3althtech@mail.ru")
                            label("Вход осуществлен") {
                                addClass(successLogin)
                            }
                        }
                    }
                    row {
                        loginField = field("Логин") {
                            hide()
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
                            hide()
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
        registerLabel = label("Регистрация на сайте") {
            onMouseClicked = OpenSiteListener("https://games.glitchless.ru/register/")
            padding = Insets(0.0, DEFAULT_MARGIN, DEFAULT_MARGIN, 23.0)
            addClass(activated)
        }

        loginButton = button("Войти в игру") {
            useMaxWidth = true
            minHeight = 36.0
            vboxConstraints {
                marginLeft = DEFAULT_MARGIN
                marginBottom = DEFAULT_MARGIN
                marginRight = DEFAULT_MARGIN
            }
            action {
                setState(InitialState())
            }
        }

        vbox(DEFAULT_MARGIN) {
            padding = Insets(DEFAULT_MARGIN)
            alignment = Pos.CENTER
            addClass(progressbox)
            progressText = label("Введите почту и пароль")
            progressBar = progressbar {
                progress = 0.0
                useMaxWidth = true
            }
            recursiveApplyToChild { addClass(disabled) }
        }
    }

    fun setState(state: BaseState) {
        runOnUi {
            setStateInternal(state)
        }
    }

    private fun setStateInternal(state: BaseState) {
        currentBaseState = state
        titleLabel.style {
            textFill = state.titleColor
        }

        if (state.loginPasswordVisible) {
            loginField.show()
            passwordField.show()
        } else {
            loginField.hide()
            passwordField.hide()
        }

        if (state.successLoginVisible) {
            loginCompleteArea.show()
        } else {
            loginCompleteArea.hide()
        }

        if (state.disableProgressBar) {
            progressBar.recursiveApplyToChild { addClass(disabled) }
        } else {
            progressBar.recursiveApplyToChild { removeClass(disabled) }
        }

        progressText.style { textFill = state.progressTextColor }

        if (state.buttonDisable) {
            loginButton.recursiveApplyToChild { addClass(disabled) }
        } else {
            loginButton.recursiveApplyToChild { removeClass(disabled) }
        }
        loginButton.text = state.buttonText

        progressText.text = state.progressTextContent
        successLoginText.text = state.successLoginText

        if (!state.isOpen) {
            Platform.exit()
        }

        if (state.registerFieldIsVisible) {
            registerLabel.show()
        } else {
            registerLabel.hide()
        }

        currentStage?.width = root.width
        currentStage?.height = root.height
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
    }
}