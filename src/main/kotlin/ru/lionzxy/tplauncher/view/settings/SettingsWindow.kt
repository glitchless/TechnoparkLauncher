package ru.lionzxy.tplauncher.view.settings

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import ru.lionzxy.tplauncher.config.Settings
import ru.lionzxy.tplauncher.exceptions.HeapSizeInvalidException
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.svgview
import ru.lionzxy.tplauncher.view.common.GlobalStylesheet
import ru.lionzxy.tplauncher.view.common.MyCheckBox
import ru.lionzxy.tplauncher.view.common.myCheckBox
import ru.lionzxy.tplauncher.view.main.listener.CloseListener
import ru.lionzxy.tplauncher.view.main.listener.MoveWindowHandler
import ru.lionzxy.tplauncher.view.main.listener.OpenSiteListener
import ru.lionzxy.tplauncher.view.settings.listener.DeleteAllListener
import ru.lionzxy.tplauncher.view.settings.listener.LogOutListener
import ru.lionzxy.tplauncher.view.settings.listener.OpenDirectoryListener
import tornadofx.*

class SettingsWindow : View() {
    private lateinit var settings: Settings

    private var titleLabel: Label by singleAssign()
    private var memmorySize: TextField by singleAssign()
    private var memmorySizeError: Label by singleAssign()
    private var javaParams: TextField by singleAssign()
    private var prefix: TextField by singleAssign()
    private var javaPath: TextField by singleAssign()
    private var debugMod: MyCheckBox by singleAssign()

    private var backButton: Button by singleAssign()
    private var applyButton: Button by singleAssign()

    override val root = stackpane {
        minWidth = 592.0
        vbox {
            titleLabel = label("games.glitchless.ru") {
                onMouseClicked =
                    OpenSiteListener("https://games.glitchless.ru")
                padding = Insets(11.5, 0.0, 0.0, 23.0)
                addClass(GlobalStylesheet.titleStyle)
            }

            form {
                padding = Insets(Constants.DEFAULT_MARGIN, Constants.DEFAULT_MARGIN, 0.0, 23.0)
                fieldset {
                    field("Объем памяти") {
                        memmorySize = textfield() {
                            textProperty().addListener { _, _, _ -> resetError() }
                        }
                    }
                    memmorySizeError = label() {
                        hide()
                        style {
                            textFill = Constants.errorColor
                        }
                    }

                    field("Параметры java") { javaParams = textfield() }
                    field("Prefix") { prefix = textfield() }
                    field("Путь до Java") { javaPath = textfield() }
                    field("Дебаг-режим") { debugMod = myCheckBox() }
                }
            }
            label("Перейти в директорию игры") {
                vboxConstraints {
                    marginLeft = 23.0
                }
                addClass(GlobalStylesheet.activated)
                onMouseClicked = OpenDirectoryListener()
            }
            label("Выйти из аккаунта") {
                vboxConstraints {
                    marginLeft = 23.0
                }
                addClass(GlobalStylesheet.activated)
                onMouseClicked = LogOutListener()
            }
            label("Удалить игру и сбросить настройки лаунчера") {
                vboxConstraints {
                    marginLeft = 23.0
                    marginBottom = Constants.DEFAULT_MARGIN
                }
                addClass(GlobalStylesheet.nonFocused)
                onMouseClicked = DeleteAllListener()
            }
            hbox() {
                alignment = Pos.CENTER
                addClass(GlobalStylesheet.progressbox)
                backButton = button("Вернуться") {
                    addClass(GlobalStylesheet.backButton)
                    hboxConstraints {
                        hGrow = Priority.ALWAYS
                        margin = Insets(Constants.DEFAULT_MARGIN)
                    }
                    useMaxWidth = true
                    action { close() }
                }
                applyButton = button("Применить") {
                    hboxConstraints {
                        hGrow = Priority.ALWAYS
                        margin = Insets(Constants.DEFAULT_MARGIN)
                    }
                    useMaxWidth = true
                    action { saveSettings() }
                }
            }
        }
        svgview("times-solid") {
            addClass(GlobalStylesheet.link)
            fitHeight = 20.0
            fitWidth = 20.0
            stackpaneConstraints {
                margin = Insets(Constants.DEFAULT_MARGIN)
            }
            alignment = Pos.TOP_RIGHT
            onMouseClicked = CloseListener(this@SettingsWindow)
        }
        val moveWindowHandler = MoveWindowHandler(this@SettingsWindow)
        onMouseDragged = moveWindowHandler
        onMousePressed = moveWindowHandler
    }.apply {
        loadSettings()
    }

    fun loadSettings() {
        settings = Settings(ConfigHelper.config.settings)
        memmorySize.text = settings.heapSize
        javaParams.text = settings.customJavaParameter
        prefix.text = settings.commandPrefix
        javaPath.text = settings.javaLocation
        debugMod.checked = settings.isDebug
    }

    fun saveSettings() {
        try {
            settings.heapSize = memmorySize.text
        } catch (exp: HeapSizeInvalidException) {
            memmorySizeError.show()
            titleLabel.style {
                textFill = Constants.errorColor
            }
            applyButton.style {
                backgroundColor = multi(Constants.errorColor)
            }
            memmorySizeError.text = exp.message
        }
        settings.customJavaParameter = javaParams.text
        settings.commandPrefix = prefix.text
        settings.javaLocation = javaPath.text
        settings.isDebug = debugMod.checked

        modalStage?.sizeToScene()
    }

    fun resetError() {
        memmorySizeError.hide()
        titleLabel.style {
            textFill = Constants.accentColor
        }
        applyButton.style {
            backgroundColor = multi(Constants.accentColor)
        }

        modalStage?.sizeToScene()
    }
}