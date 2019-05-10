package ru.lionzxy.tplauncher.view.main.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import tornadofx.View

class MoveWindowHandler(private val view: View) : EventHandler<MouseEvent> {
    //define your offsets here
    private var xOffset = 0.0
    private var yOffset = 0.0

    override fun handle(event: MouseEvent) {
        if (event.eventType == MouseEvent.MOUSE_PRESSED) {
            xOffset = event.sceneX;
            yOffset = event.sceneY;
            return
        }

        if (event.eventType == MouseEvent.MOUSE_DRAGGED) {
            view.modalStage?.x = event.screenX - xOffset;
            view.modalStage?.y = event.screenY - yOffset;
        }
    }

}