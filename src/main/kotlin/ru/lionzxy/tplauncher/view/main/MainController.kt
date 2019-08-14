package ru.lionzxy.tplauncher.view.main

import io.sentry.Sentry
import io.sentry.event.User
import ru.lionzxy.tplauncher.SENTRY
import ru.lionzxy.tplauncher.downloader.ComposerDownloader
import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.utils.LogoUtils
import ru.lionzxy.tplauncher.utils.MinecraftModpack
import ru.lionzxy.tplauncher.utils.runAsync
import ru.lionzxy.tplauncher.view.main.states.*
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.IOException
import java.lang.Thread.sleep
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class MainController(val stateMachine: IImplementState, val progressMonitor: IProgressMonitor) {
    val minecraftAccountManager = MinecraftAccountManager()

    fun onInitView() {
        if (minecraftAccountManager.isLogged) {
            stateMachine.setState(LoggedState(minecraftAccountManager.getEmail()))
            return
        }
        stateMachine.setState(InitialState())
    }

    fun onButtonClick(email: String, password: String) = runAsync {
        if (stateMachine.currentState() is InitialState) {
            onLogin(email, password)
            return@runAsync
        }

        if (stateMachine.currentState() is LoggedState) {
            onGameStart()
            return@runAsync
        }
    }

    fun onLogin(email: String, password: String) {
        if (!email.contains("@")) {
            stateMachine.setState(ErrorInitialState("Введите валидную почту"))
            return
        }

        if (password.isNullOrEmpty()) {
            stateMachine.setState(ErrorInitialState("Пароль не может быть пустым"))
            return
        }

        stateMachine.setState(LoginProgressState())
        progressMonitor.setStatus("Авторизация по email $email...")
        progressMonitor.setProgress(-1)

        try {
            minecraftAccountManager.login(email, password)
        } catch (exp: YDServiceAuthenticationException) {
            exp.printStackTrace()
            stateMachine.setState(ErrorInitialState(exp.reason ?: exp.localizedMessage))
            return
        } catch (ioExp: IOException) {
            ioExp.printStackTrace()
            stateMachine.setState(ErrorInitialState("Проверьте подключение к интернету"))
            return
        }
        onGameStart(LoggedState(email))
    }

    fun onGameStart(baseState: BaseState = stateMachine.currentState()) {
        val currentState = baseState as? LoggedState ?: return
        stateMachine.setState(GameLoadingState(currentState))
        progressMonitor.setProgress(-1)

        val downloader = ComposerDownloader(minecraftAccountManager)
        try {
            downloader.downloadAll(progressMonitor)
            LogoUtils.setLogoForMinecraft(MinecraftModpack.MIDGARD)
            minecraftAccountManager.launch()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            stateMachine.setState(ErrorInitialState("Проверьте подключение к интернету"))
            return
        } catch (e: Exception) {
            Sentry.capture(e)
            stateMachine.setState(
                ErrorLaunchGameState(
                    currentState,
                    "Внутреняя ошибка, мы уже исправляем это"
                )
            )
            e.printStackTrace()
            return
        }


        stateMachine.setState(MinecraftRunningState(currentState))
        progressMonitor.setStatus("Запускаем Minecraft...")
        progressMonitor.setProgress(-1)
        sleep(TimeUnit.MINUTES.toMillis(1))
        stateMachine.setState(MinecraftLaunchedState(currentState))
    }

    fun onPasswordOrLoginChange() {
        if (stateMachine.currentState() is ErrorInitialState) {
            stateMachine.setState(InitialState())
        }
    }
}