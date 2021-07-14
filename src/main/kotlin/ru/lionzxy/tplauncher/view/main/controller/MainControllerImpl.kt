package ru.lionzxy.tplauncher.view.main.controller

import com.squareup.anvil.annotations.ContributesBinding
import io.sentry.Sentry
import ru.lionzxy.tplauncher.di.AppScope
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.prepare.ComposePrepare
import ru.lionzxy.tplauncher.services.auth.MinecraftAccountService
import ru.lionzxy.tplauncher.services.modpack.MinecraftModpackService
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
import ru.lionzxy.tplauncher.utils.runAsync
import ru.lionzxy.tplauncher.view.main.states.*
import sk.tomsik68.mclauncher.api.ui.IProgressMonitor
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.IOException
import java.lang.Thread.sleep
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MainControllerImpl @Inject constructor(
    private val stateMachine: IImplementState,
    private val progressMonitor: IProgressMonitor,
    private val accountService: MinecraftAccountService,
    private val modpackService: MinecraftModpackService
) : MainController {
    override fun onInitView() {
        if (accountService.isLogged()) {
            val email = accountService.getActiveSession()?.email
            if (email != null) {
                stateMachine.setState(LoggedState(email))
                return
            }
        }
        stateMachine.setState(InitialState())
    }

    override fun onButtonClick(email: String, password: String) = runAsync {
        if (stateMachine.currentState() is InitialState) {
            onLogin(email, password)
            return@runAsync
        }

        if (stateMachine.currentState() is LoggedState) {
            onGameStart()
            return@runAsync
        }
    }

    override fun onLogin(email: String, password: String) {
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
            accountService.login(email, password)
        } catch (exp: YDServiceAuthenticationException) {
            exp.printStackTrace()
            stateMachine.setState(ErrorInitialState(exp.reason?.error ?: exp.localizedMessage))
            return
        } catch (ioExp: IOException) {
            ioExp.printStackTrace()
            stateMachine.setState(ErrorInitialState("Проверьте подключение к интернету"))
            return
        }
        onGameStart(LoggedState(email))
    }

    override fun onGameStart(state: BaseState?) {
        val baseState = state ?: stateMachine.currentState()
        val currentState = baseState as? LoggedState ?: return
        stateMachine.setState(GameLoadingState(currentState))
        progressMonitor.setProgress(-1)

        val prepareManager = ComposePrepare()

        try {
            val context = MinecraftContext(progressMonitor, modpackService.getCurrentModpack())
            prepareManager.prepareMinecraft(context)
            LogoUtils.setLogoForMinecraft(modpackService.getCurrentModpack())
            context.launch(accountService.getActiveSession()!!)
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

    override fun onChangeModpack(newPack: MinecraftModpack) {
        modpackService.setCurrentModpack(newPack)
        ConfigHelper.writeToConfig {
            currentModpack = modpackService.getCurrentModpack()
        }
        stateMachine.setState(stateMachine.currentState())
    }

    override fun onPasswordOrLoginChange() {
        if (stateMachine.currentState() is ErrorInitialState) {
            stateMachine.setState(InitialState())
        }
    }
}
