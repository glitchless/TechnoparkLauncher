package ru.lionzxy.tplauncher.view.common

import ru.lionzxy.tplauncher.utils.Constants
import ru.lionzxy.tplauncher.utils.ResourceHelper
import tornadofx.*

class GlobalStylesheet : Stylesheet() {
    companion object {
        val textFont = ResourceHelper.getFont("Roboto-Regular.ttf", 14.0)
        val activated by csspseudoclass()
        val progressbox by cssclass()
    }

    init {
        root {
            backgroundColor = multi(Constants.backgroundColor)
        }
        label {
            font = textFont
            textFill = Constants.textColor
            and(disabled) {
                textFill = Constants.textDisableColor
            }
            and(activated) {
                textFill = Constants.accentColor
                underline = true
            }
        }
        arrow {
            backgroundColor = multi(Constants.secondColor)
            shape =
                "M7.85598 14.1563L0.481353 6.94431C0.125682 6.59649 0.125682 6.03257 0.481353 5.68478L1.34149 4.84362C1.69655 4.49639 2.27201 4.49572 2.62791 4.84214L8.49996 10.5578L14.372 4.84214C14.7279 4.49572 15.3033 4.49639 15.6584 4.84362L16.5185 5.68478C16.8742 6.03261 16.8742 6.59652 16.5185 6.94431L9.14395 14.1563C8.78828 14.5041 8.21165 14.5041 7.85598 14.1563Z"
            and(disabled) {
                backgroundColor = multi(Constants.disableColor)
            }
        }
        field and disabled {
            textFill = Constants.textDisableColor
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
        s(comboBox, listCell) {
            backgroundColor = multi(Constants.inputBackgroundColor)
            font = textFont
            textFill = Constants.textColor
            focusColor = Constants.textColor
            and(disabled) {
                textFill = Constants.textDisableColor
            }
        }
        button {
            backgroundColor = multi(Constants.accentColor)
            font = textFont
            fontSize = 16.px
            textFill = Constants.textColor
        }
        progressbox {
            backgroundColor = multi(Constants.backgroundDarkColor)
            label {
                textFill = Constants.backgroundProgressBarColor
            }
            and(disabled) {
                label {
                    textFill = Constants.disableProgressBarColor
                }
            }
        }
        progressBar {
            backgroundRadius = multi(box(5.px))
            track {
                backgroundColor = multi(Constants.backgroundProgressBarColor)
            }
            and(disabled) {
                track {
                    backgroundColor = multi(Constants.disableProgressBarColor)
                }
            }
            accentColor = Constants.accentColor
        }
    }
}