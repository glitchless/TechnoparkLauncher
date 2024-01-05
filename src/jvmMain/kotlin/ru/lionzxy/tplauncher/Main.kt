package ru.lionzxy.tplauncher

import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
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
            Text("Hello, launcher")
        }
    }
}
