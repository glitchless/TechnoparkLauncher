package ru.lionzxy.tplauncher.view.main.states

import ru.lionzxy.tplauncher.utils.Constants

class ErrorLaunchGameState(email: String, error: String) : LoggedState(email) {
    constructor(loggedState: LoggedState, error: String) : this(loggedState.email, error)

    override var titleColor = Constants.errorColor
    override var progressTextColor = Constants.errorColor
    override var progressTextContent: String? = error
}