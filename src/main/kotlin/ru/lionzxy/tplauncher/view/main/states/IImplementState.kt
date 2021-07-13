package ru.lionzxy.tplauncher.view.main.states

/**
 * State machine for view
 */
interface IImplementState {
    fun setState(state: BaseState)
    fun currentState(): BaseState
}
