package ru.lionzxy.tplauncher.view.main.states

open class GameLoadingState(email: String) : LoggedState(email) {
    constructor(loggedState: LoggedState) : this(loggedState.email)

    override var disableProgressBar = false
    override var buttonDisable = true
    override var disableInputField = true
    override var progressTextContent: String? = "Загружаем игру..."
    override var disableSelectModpack = true
}
