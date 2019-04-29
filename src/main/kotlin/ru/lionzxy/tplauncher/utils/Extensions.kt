package ru.lionzxy.tplauncher.utils

import javafx.application.Platform

inline fun runOnUi(crossinline invoke: () -> Unit) {
    Platform.runLater { invoke.invoke() }
}