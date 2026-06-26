package ru.lionzxy.tplauncher.view.main.states

open class InitialState() : BaseState() {
    override var registerFieldIsVisible = true
    override var progressTextContent: String? = "Введите логин и пароль"
}
