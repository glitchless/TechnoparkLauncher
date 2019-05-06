package ru.lionzxy.tplauncher.view.common

import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.ResourceHelper
import tornadofx.Stylesheet
import tornadofx.multi

class GlobalStylesheet : Stylesheet() {
    companion object {
        val textFont = ResourceHelper.getFont("Roboto-Regular.ttf", 14.0)
    }

    init {
        root {
            backgroundColor = multi(Constants.backgroundColor)
        }
        label {
            font = textFont
            textFill = Constants.textColor
        }
        fieldset {
            font = textFont
            textFill = Constants.textColor
        }
        textField {
            backgroundColor = multi(Constants.inputBackgroundColor)
            font = textFont
            textFill = Constants.textColor
        }
        comboBox {
            backgroundColor = multi(Constants.inputBackgroundColor)
            font = textFont
            textFill = Constants.textColor
        }
    }
}