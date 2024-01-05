package ru.lionzxy.tplauncher.ui.main.model

import ru.lionzxy.tplauncher.ui.Constants

open class BaseState() {
    open var titleColor = Constants.accentColor
    open var loginPasswordVisible = true
    open var successLoginVisible = false
    open var disableProgressBar = true
    open var disableInputField = false
    open var progressTextColor = Constants.backgroundProgressBarColor
    open var progressTextContent: String? = "Загрузка"
    open var buttonDisable = false
    open val buttonText = "Войти в игру"
    open var successLoginText = "example@example.com"
    open var isOpen = true
    open var registerFieldIsVisible = false
    open var registerFieldColor = Constants.accentColor
    open var settingsFieldIsClickable = true
    open var disableSelectModpack = false

    open class InitialState() : BaseState() {
        override var registerFieldIsVisible = true
        override var progressTextContent: String? = "Введите логин и пароль"

        class ErrorInitialState(error: String) : InitialState() {
            override var titleColor = Constants.errorColor
            override var progressTextColor = Constants.errorColor
            override var progressTextContent: String? = error
            override var buttonDisable = true
            override var buttonText = error
            override var registerFieldColor = Constants.errorColor
        }
    }

    open class LoggedState(val email: String) : BaseState() {
        override var loginPasswordVisible = false
        override var successLoginVisible = true
        override var successLoginText = email
        override var progressTextContent: String? = "Приятной игры"

        open class GameLoadingState(email: String) : LoggedState(email) {
            constructor(loggedState: LoggedState) : this(loggedState.email)

            override var disableProgressBar = false
            override var buttonDisable = true
            override var disableInputField = true
            override var progressTextContent: String? = "Загружаем игру..."
            override var disableSelectModpack = true

            class MinecraftLaunchedState(state: LoggedState) : GameLoadingState(state) {
                override var isOpen = false
            }

            class MinecraftRunningState(loggedState: LoggedState) : GameLoadingState(loggedState) {
                override var settingsFieldIsClickable = false
            }
        }

        class ErrorLaunchGameState(email: String, error: String) : LoggedState(email) {
            constructor(loggedState: LoggedState, error: String) : this(loggedState.email, error)

            override var titleColor = Constants.errorColor
            override var progressTextColor = Constants.errorColor
            override var progressTextContent: String? = error
        }
    }

    class LoginProgressState : BaseState() {
        override var disableProgressBar = false
        override var buttonDisable = true
        override var disableInputField = true
        override var disableSelectModpack = true
    }
}
