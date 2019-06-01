package ru.lionzxy.tplauncher.view.main

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import ru.lionzxy.tplauncher.Main
import ru.lionzxy.tplauncher.utils.Constants.DEFAULT_MARGIN
import ru.lionzxy.tplauncher.utils.recursiveDisable
import ru.lionzxy.tplauncher.utils.recursiveEnable
import ru.lionzxy.tplauncher.utils.runOnUi
import ru.lionzxy.tplauncher.utils.svgview
import ru.lionzxy.tplauncher.view.common.Avatar
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.activated
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.link
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.progressbox
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.successLogin
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet.Companion.titleStyle
import ru.lionzxy.tplauncher.view.common.avatarimage
import ru.lionzxy.tplauncher.view.main.listener.CloseApplicationListener
import ru.lionzxy.tplauncher.view.main.listener.MoveWindowHandler
import ru.lionzxy.tplauncher.view.main.listener.OpenSettingsListener
import ru.lionzxy.tplauncher.view.main.listener.OpenSiteListener
import ru.lionzxy.tplauncher.view.main.states.BaseState
import ru.lionzxy.tplauncher.view.main.states.IImplementState
import tornadofx.*

class MainWindow : View(), IImplementState {
    //Delegates
    public var progressDelegate = ProgressDelegate()

    //View for state
    private var loginCompleteArea: HBox by singleAssign()
    private var avatar: Avatar by singleAssign()
    private var loginField: Field by singleAssign()
    private var passwordField: Field by singleAssign()
    private var titleLabel: Label by singleAssign()
    private var loginButton: Button by singleAssign()
    private var successLoginText: Label by singleAssign()
    private var registerLabel: Label by singleAssign()
    private var currentBaseState: BaseState = BaseState()
    private var settingsField: HBox by singleAssign()
    private var progressBar = ProgressBar()
        set(value) {
            field = value
            progressDelegate.progressBar = value
        }
    private var progressText = Label()
        set(value) {
            field = value
            progressDelegate.progressText = value
        }

    //View for controller
    private var loginInput: TextField by singleAssign()
    private var passwordInput: TextField by singleAssign()

    private var controller: MainController = MainController(this, progressDelegate)

    init {
        controller.onInitView()
    }

    override val root = stackpane {
        maxWidth = 592.0
        vbox {
            titleLabel = label("games.glitchless.ru") {
                padding = Insets(11.5, 0.0, 0.0, 23.0)
                addClass(titleStyle)
            }

            form {
                padding = Insets(DEFAULT_MARGIN, DEFAULT_MARGIN, 0.0, 23.0)
                fieldset() {
                    alignment = Pos.CENTER
                    gridpane {
                        loginCompleteArea = hbox {
                            hide()
                            alignment = Pos.CENTER_LEFT
                            minWidth = 302.0
                            minHeight = 96.0
                            gridpaneConstraints {
                                rowSpan = 2
                            }
                            avatar = avatarimage()
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
                            loginField = field("Email") {
                                loginInput = textfield() {
                                    textProperty().addListener { _, _, _ -> controller.onPasswordOrLoginChange() }
                                }
                            }
                            field("Сервер") {
                                gridpaneConstraints {
                                    marginLeft = DEFAULT_MARGIN * 2
                                }
                                combobox<String> {
                                    hgrow = Priority.ALWAYS
                                    maxWidth = Double.MAX_VALUE
                                    items = FXCollections.observableArrayList("Medium")
                                    selectionModel.select(0)
                                    isDisable = true
                                }
                                recursiveDisable()
                            }
                        }
                        row {
                            passwordField = field("Пароль") {
                                passwordInput = passwordfield() {
                                    textProperty().addListener { _, _, _ -> controller.onPasswordOrLoginChange() }
                                }
                            }

                            settingsField = hbox {
                                addClass(link)
                                onMouseClicked = OpenSettingsListener()
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
                onMouseClicked =
                    OpenSiteListener("https://games.glitchless.ru/register/")
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
                    controller.onButtonClick(loginInput.text, passwordInput.text)
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
                recursiveDisable()
            }
        }
        svgview("times-solid") {
            addClass(link)
            fitHeight = 20.0
            fitWidth = 20.0
            stackpaneConstraints {
                margin = Insets(DEFAULT_MARGIN)
            }
            alignment = Pos.TOP_RIGHT
            onMouseClicked = CloseApplicationListener()
        }
        val moveWindowHandler = MoveWindowHandler(this@MainWindow)
        onMouseDragged = moveWindowHandler
        onMousePressed = moveWindowHandler
    }

    override fun setState(state: BaseState) {
        runOnUi {
            setStateInternal(state)
        }
    }

    override fun currentState() = currentBaseState

    private fun setStateInternal(state: BaseState) {
        currentBaseState = state
        titleLabel.style {
            textFill = state.titleColor
        }

        if (state.disableInputField) {
            loginField.recursiveDisable()
            passwordField.recursiveDisable()
            passwordInput.isEditable = false
            loginInput.isEditable = false
        } else {
            loginField.recursiveEnable()
            passwordField.recursiveEnable()
            passwordInput.isEditable = true
            loginInput.isEditable = true
        }

        if (state.loginPasswordVisible) {
            loginField.show()
            passwordField.show()
        } else {
            loginField.hide()
            passwordField.hide()
        }

        if (state.successLoginVisible) {
            avatar.show()
            loginCompleteArea.show()
        } else {
            avatar.hide()
            loginCompleteArea.hide()
        }

        if (state.disableProgressBar) {
            progressBar.recursiveDisable()
            progressBar.progress = 0.0
        } else {
            progressBar.recursiveEnable()
        }

        progressText.style {
            textFill = state.progressTextColor
        }

        if (state.buttonDisable) {
            loginButton.recursiveDisable()
        } else {
            loginButton.recursiveEnable()
        }
        loginButton.text = state.buttonText

        state.progressTextContent?.let { progressText.text = it }
        successLoginText.text = state.successLoginText

        if (!state.isOpen) {
            Main.closeApplication()
        }

        if (state.registerFieldIsVisible) {
            registerLabel.show()
        } else {
            registerLabel.hide()
        }
        registerLabel.style { textFill = state.registerFieldColor }

        if (state.settingsFieldIsClickable) {
            settingsField.onMouseClicked = OpenSettingsListener()
            settingsField.recursiveEnable()
        } else {
            settingsField.onMouseClicked = null
            settingsField.recursiveDisable()
        }

        primaryStage.sizeToScene()
    }
}