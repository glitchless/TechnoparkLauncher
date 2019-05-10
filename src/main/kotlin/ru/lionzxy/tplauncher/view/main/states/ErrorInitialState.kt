package ru.lionzxy.tplauncher.view.main.states

import ru.lionzxy.tplauncher.utils.Constants

class ErrorInitialState(error: String) : InitialState() {
    override var titleColor = Constants.errorColor
    override var progressTextColor = Constants.errorColor
    override var progressTextContent: String? = error
    override var buttonDisable = true
    override val buttonText = error
}