package ru.lionzxy.tplauncher.view.main.listener

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import javafx.stage.Stage

class MoveWindowHandler(private val stage: Stage) : EventHandler<MouseEvent> {
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
            stage.x = event.screenX - xOffset;
            stage.y = event.screenY - yOffset;
        }
    }

}