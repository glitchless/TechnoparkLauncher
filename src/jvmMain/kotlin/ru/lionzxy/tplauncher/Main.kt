package ru.lionzxy.tplauncher

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.*
import ru.lionzxy.tplauncher.ui.archerror.ArchUnsupportedErrorComposable
import ru.lionzxy.tplauncher.ui.main.MainComposable
import ru.lionzxy.tplauncher.ui.main.model.BaseState
import ru.lionzxy.tplauncher.utils.Arch
import ru.lionzxy.tplauncher.utils.OperatingSystem

fun main() {
    val exceptionOrNull = runCatching {
        OperatingSystem.current()
        Arch.current()
    }.exceptionOrNull()
    application {
        val application = this
        val windowState = rememberWindowState(
            size = DpSize.Unspecified,
            position = WindowPosition.Aligned(Alignment.Center)
        )

        Window(
            state = windowState,
            onCloseRequest = ::exitApplication,
            resizable = false,
            undecorated = true
        ) {
            if (exceptionOrNull != null) {
                ArchUnsupportedErrorComposable(exceptionOrNull, onClose = application::exitApplication)
            } else {
                WindowDraggableArea {
                    MainComposable(state = BaseState(), onClose = application::exitApplication)
                }
            }
        }
    }
}
