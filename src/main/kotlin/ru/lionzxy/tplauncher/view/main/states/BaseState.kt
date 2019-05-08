package ru.lionzxy.tplauncher.view.main.states

import ru.lionzxy.tplauncher.utils.Constants

open class BaseState {
    open var titleColor = Constants.accentColor
    open var loginPasswordVisible = true
    open var successLoginVisible = false
    open var disableProgressBar = true
    open var progressTextColor = Constants.backgroundProgressBarColor
    open var progressTextContent = "Введите логин и пароль"
    open var buttonDisable = false
    open val buttonText = "Войти в игру"
    open var successLoginText = "example@example.com"
    open var isOpen = true
    open var registerFieldIsVisible = false
}