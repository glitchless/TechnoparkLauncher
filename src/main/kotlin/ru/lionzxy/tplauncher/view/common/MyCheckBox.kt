package ru.lionzxy.tplauncher.view.common

import javafx.event.EventHandler
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import ru.lionzxy.tplauncher.utils.svgview
import tornadofx.addClass
import tornadofx.attachTo
import tornadofx.hide
import tornadofx.show

class MyCheckBox : StackPane(), EventHandler<MouseEvent> {
    var checkSolid: ImageView? = null
    var checked = false
        set(value) {
            if (value) {
                checkSolid?.show()
            } else {
                checkSolid?.hide()
            }
            field = value
        }

    init {
        maxHeight = 28.0
        maxWidth = 28.0
        prefHeight = 28.0
        addClass(GlobalStylesheet.myCheckBox)
        checkSolid = svgview("check-solid") {
            alignment = Pos.CENTER
            fitHeight = 16.0
            fitWidth = 16.0
        }
        onMouseClicked = this
    }

    override fun handle(clickEvent: MouseEvent?) {
        checked = !checked
    }
}

fun EventTarget.myCheckBox(op: MyCheckBox.() -> Unit = {}): MyCheckBox {
    return MyCheckBox().attachTo(this, op)
}