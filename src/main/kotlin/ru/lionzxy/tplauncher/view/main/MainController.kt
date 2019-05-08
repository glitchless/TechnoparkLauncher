package ru.lionzxy.tplauncher.view.main

import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.view.main.states.IImplementState
import ru.lionzxy.tplauncher.view.main.states.InitialState
import ru.lionzxy.tplauncher.view.main.states.LoggedState

class MainController(val stateMachine: IImplementState) {
    fun onInitView() {
        stateMachine.setState(InitialState())
    }

    fun onLogin(email: String, password: String) {
        //TODO add error example
        if (!email.isEmpty()) {
            stateMachine.setState(LoggedState(email))
            return
        }

        val login = ConfigHelper.config.profile?.login
        if (!login.isNullOrEmpty()) {
            stateMachine.setState(LoggedState(login!!))
            return
        }

        stateMachine.setState(LoggedState("example@example.com"))
    }
}