package ru.lionzxy.tplauncher.view.main.states

open class LoggedState(val email: String) : BaseState() {
    override var loginPasswordVisible = false
    override var successLoginVisible = true
    override var successLoginText = email
    override var progressTextContent: String? = "Приятной игры"
}