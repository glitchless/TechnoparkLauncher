package ru.lionzxy.tplauncher

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// NOTE: compose.desktop.currentOs bundles Material 2 + foundation + ui, NOT material3.
// Use foundation's BasicText (matches the bespoke no-Material mandate). Do not add material3.
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TechnoparkLauncher") {
        BasicText("TechnoparkLauncher :desktop bring-up")
    }
}
