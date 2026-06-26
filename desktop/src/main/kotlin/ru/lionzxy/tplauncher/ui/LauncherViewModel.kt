package ru.lionzxy.tplauncher.ui

import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.prepare.ComposePrepare
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.IOException
import java.net.UnknownHostException

class LauncherViewModel(private val scope: CoroutineScope) {

    private val _progress = MutableStateFlow(ProgressUiState())
    val progress: StateFlow<ProgressUiState> = _progress.asStateFlow()

    private val progressMonitorBridge = ProgressMonitorBridge(_progress)

    private var context = MinecraftContext(
        progressMonitorBridge,
        ConfigHelper.config.currentModpack,
        MinecraftAccountManager(ConfigHelper.config.currentModpack)
    )

    private val _state = MutableStateFlow<LauncherState>(LauncherState.Initial)
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    fun onInitView() {
        if (context.minecraftAccountManager.isLogged) {
            _state.value = LauncherState.Logged(context.minecraftAccountManager.getEmail())
            return
        }
        _state.value = LauncherState.Initial
    }

    fun onButtonClick(email: String, password: String) {
        val current = _state.value
        if (current is LauncherState.Initial || current is LauncherState.InitialError) {
            onLogin(email, password)
            return
        }

        if (current is LauncherState.Logged || current is LauncherState.LaunchError) {
            val loggedEmail = when (current) {
                is LauncherState.Logged -> current.email
                is LauncherState.LaunchError -> current.email
                else -> return // unreachable
            }
            scope.launch(Dispatchers.IO) {
                onGameStart(loggedEmail)
            }
            return
        }
    }

    fun onLogin(email: String, password: String) {
        // Validate synchronously before dispatching to network
        if (!email.contains("@")) {
            _state.value = LauncherState.InitialError(Strings.enterValidEmail)
            return
        }

        if (password.isEmpty()) {
            _state.value = LauncherState.InitialError(Strings.passwordCannotBeEmpty)
            return
        }

        _state.value = LauncherState.LoginProgress
        progressMonitorBridge.setStatus(Strings.authByEmail(email))
        progressMonitorBridge.setProgress(-1)

        scope.launch(Dispatchers.IO) {
            try {
                context.minecraftAccountManager.login(email, password)
            } catch (exp: YDServiceAuthenticationException) {
                exp.printStackTrace()
                _state.value = LauncherState.InitialError(
                    exp.reason?.error ?: exp.localizedMessage ?: Strings.checkInternetConnection
                )
                return@launch
            } catch (ioExp: IOException) {
                ioExp.printStackTrace()
                _state.value = LauncherState.InitialError(Strings.checkInternetConnection)
                return@launch
            }
            onGameStart(email)
        }
    }

    private fun onGameStart(email: String) {
        _state.value = LauncherState.GameLoading(email)
        progressMonitorBridge.setProgress(-1)

        try {
            ComposePrepare().prepareMinecraft(context)
            LogoUtils.setLogoForMinecraft(context)
            context.launch()
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            _state.value = LauncherState.InitialError(Strings.checkInternetConnection)
            return
        } catch (e: Exception) {
            Sentry.captureException(e)
            _state.value = LauncherState.LaunchError(email, Strings.internalError)
            e.printStackTrace()
            return
        }

        _state.value = LauncherState.MinecraftRunning(email)
        progressMonitorBridge.setStatus(Strings.launchingMinecraft)
        progressMonitorBridge.setProgress(-1)
        scope.launch(Dispatchers.IO) {
            delay(60_000)
            _state.value = LauncherState.MinecraftLaunched(email)
        }
    }

    fun onChangeModpack(pack: MinecraftModpack) {
        context = MinecraftContext(progressMonitorBridge, pack, MinecraftAccountManager(pack))
        ConfigHelper.writeToConfig {
            currentModpack = pack
        }
        _state.value = _state.value
    }

    fun onPasswordOrLoginChange() {
        if (_state.value is LauncherState.InitialError) {
            _state.value = LauncherState.Initial
        }
    }
}
