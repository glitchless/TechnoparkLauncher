package ru.lionzxy.tplauncher.view.main.states

class LoggedState(email: String) : BaseState() {
    override var loginPasswordVisible = false
    override var successLoginVisible = true
    override var successLoginText = email
}