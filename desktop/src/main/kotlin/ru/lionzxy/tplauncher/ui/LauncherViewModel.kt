package ru.lionzxy.tplauncher.ui

import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.lionzxy.tplauncher.log.Logger
import ru.lionzxy.tplauncher.minecraft.MinecraftAccountManager
import ru.lionzxy.tplauncher.minecraft.MinecraftContext
import ru.lionzxy.tplauncher.minecraft.MinecraftModpack
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityRepair
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityRepairOrchestrator
import ru.lionzxy.tplauncher.prepare.ComposePrepare
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
import java.io.File
import java.io.IOException

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

    private var cachedRepair: ConnectivityRepairOrchestrator? = null

    /** Lazily-built connectivity-repair orchestrator for the current modpack (reset on modpack change). */
    private fun repairOrchestrator(): ConnectivityRepairOrchestrator =
        cachedRepair ?: ConnectivityRepair.forModpack(
            javaCode = context.modpack.javaCode,
            scriptDir = File(System.getProperty("java.io.tmpdir")),
        ).also { cachedRepair = it }

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
                Logger.e("Login", "Authentication failed", exp)
                _state.value = LauncherState.InitialError(
                    exp.reason?.error ?: exp.localizedMessage ?: Strings.checkInternetConnection
                )
                return@launch
            } catch (ioExp: IOException) {
                Logger.e("Login", "Network error during login", ioExp)
                _state.value = if (ConnectivityBlockClassifier.isPermissionDeniedSocket(ioExp)) {
                    LauncherState.InitialError(Strings.connectionBlocked)
                } else {
                    LauncherState.InitialError(Strings.checkInternetConnection)
                }
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
        } catch (e: Exception) {
            Logger.e("Launcher", "Failed to prepare/launch Minecraft", e)
            if (ConnectivityBlockClassifier.isPermissionDeniedSocket(e)) {
                val assessment = repairOrchestrator().assess()
                _state.value = LauncherState.ConnectivityBlocked(
                    email = email,
                    message = connectivityBlockedMessage(assessment.products, assessment.avClass),
                    canFirewallFix = assessment.firewallFixFirst,
                )
                return
            }
            val mapping = mapLaunchError(email, e)
            if (mapping.reportToSentry) Sentry.captureException(e)
            _state.value = mapping.state
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

    /**
     * "Разрешить доступ" on the [LauncherState.ConnectivityBlocked] panel: attempt the elevated
     * Windows Firewall fix (a single UAC prompt), then retry the launch. If the fix restored
     * connectivity the launch proceeds online; otherwise the retry still launches an already-installed
     * pack via the offline fallback, or re-enters ConnectivityBlocked.
     */
    fun onConnectivityFix() {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        scope.launch(Dispatchers.IO) {
            _state.value = LauncherState.GameLoading(blocked.email)
            repairOrchestrator().tryFirewallFix()
            onGameStart(blocked.email)
        }
    }

    /**
     * "Повторить" on the [LauncherState.ConnectivityBlocked] panel: retry the launch without touching
     * the firewall (e.g. after the user added an exception in their antivirus). An already-installed
     * pack launches via the offline fallback even if the network is still blocked.
     */
    fun onConnectivityRetry() {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        scope.launch(Dispatchers.IO) {
            onGameStart(blocked.email)
        }
    }

    fun onChangeModpack(pack: MinecraftModpack) {
        context = MinecraftContext(progressMonitorBridge, pack, MinecraftAccountManager(pack))
        cachedRepair = null
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
