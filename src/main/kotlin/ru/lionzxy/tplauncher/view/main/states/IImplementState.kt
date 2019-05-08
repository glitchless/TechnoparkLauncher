package ru.lionzxy.tplauncher.view.main.states

interface IImplementState {
    fun setState(state: BaseState)
    fun currentState(): BaseState
}