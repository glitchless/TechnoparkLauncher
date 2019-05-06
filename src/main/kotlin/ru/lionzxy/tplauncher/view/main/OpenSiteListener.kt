package ru.lionzxy.tplauncher.view.main

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import ru.lionzxy.tplauncher.utils.openInBrowser

class OpenSiteListener(val site: String) : EventHandler<MouseEvent> {
    override fun handle(p0: MouseEvent?) {
        site.openInBrowser()
    }
}