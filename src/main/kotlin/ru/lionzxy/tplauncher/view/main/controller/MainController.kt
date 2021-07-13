package ru.lionzxy.tplauncher.view.main.controller

import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.view.main.states.BaseState

interface MainController {
    fun onInitView()
    fun onButtonClick(email: String, password: String)
    fun onLogin(email: String, password: String)
    fun onGameStart(state: BaseState? = null)
    fun onChangeModpack(newPack: MinecraftModpack)
    fun onPasswordOrLoginChange()
}
