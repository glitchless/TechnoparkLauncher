package ru.lionzxy.tplauncher.view.main.states

class MinecraftRunningState(loggedState: LoggedState) : GameLoadingState(loggedState) {
    override var settingsFieldIsClickable = false
}