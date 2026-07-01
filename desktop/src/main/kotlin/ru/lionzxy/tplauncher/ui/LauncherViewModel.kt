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
import ru.lionzxy.tplauncher.minecraft.connectivity.AvClass
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityBlockClassifier
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityRepair
import ru.lionzxy.tplauncher.minecraft.connectivity.ConnectivityRepairOrchestrator
import ru.lionzxy.tplauncher.minecraft.connectivity.RepairOutcome
import ru.lionzxy.tplauncher.prepare.ComposePrepare
import ru.lionzxy.tplauncher.ui.state.LauncherState
import ru.lionzxy.tplauncher.ui.state.ProgressUiState
import ru.lionzxy.tplauncher.utils.ConfigHelper
import ru.lionzxy.tplauncher.utils.LogoUtils
import sk.tomsik68.mclauncher.impl.login.yggdrasil.YDServiceAuthenticationException
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
            // The launcher's own temp dir: ConfigHelper wipes it on every startup, so the
            // admin-executed repair script never accumulates in a user-writable location.
            scriptDir = ConfigHelper.getTemporaryDirectory(),
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

        // Defensive: the repair panel has its own buttons, but if the flags-driven main button is
        // ever rendered for this state, route it to the action its label promises.
        if (current is LauncherState.ConnectivityBlocked) {
            if (current.canFirewallFix) onConnectivityFix() else onConnectivityRetry()
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

    private fun onGameStart(email: String, tolerateConnectivityBlock: Boolean = false) {
        _state.value = LauncherState.GameLoading(email)
        progressMonitorBridge.setProgress(-1)
        // Only an explicit retry from the ConnectivityBlocked screen launches an installed pack
        // offline through a WSAEACCES block; a normal start surfaces the repair assistant instead.
        context.tolerateConnectivityBlock = tolerateConnectivityBlock

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
     * Windows Firewall fix (a single UAC prompt) and react to the verified outcome —
     * [RepairOutcome.Repaired] retries the launch (now online), [RepairOutcome.Cancelled] (declined
     * UAC) returns to the panel with a clear message, and [RepairOutcome.Guidance] (rules applied
     * but still blocked) returns to the panel with AV-specific guidance and no firewall button.
     */
    fun onConnectivityFix() {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        scope.launch(Dispatchers.IO) {
            _state.value = LauncherState.GameLoading(blocked.email)
            when (val outcome = repairOrchestrator().tryFirewallFix()) {
                is RepairOutcome.Repaired -> onGameStart(blocked.email)

                is RepairOutcome.Cancelled -> _state.value = blocked.copy(message = Strings.uacDeclined)

                is RepairOutcome.Guidance -> _state.value = blocked.copy(
                    message = if (outcome.products.isEmpty()) {
                        Strings.firewallFixDidNotHelp
                    } else {
                        // The firewall rule didn't help → the third-party product owns the block.
                        connectivityBlockedMessage(outcome.products, AvClass.THIRD_PARTY_NETWORK)
                    },
                    canFirewallFix = false,
                )
            }
        }
    }

    /**
     * "Повторить" on the [LauncherState.ConnectivityBlocked] panel: retry the launch without touching
     * the firewall (e.g. after the user added an exception in their antivirus). An already-installed
     * pack launches via the offline fallback even if the network is still blocked
     * (tolerateConnectivityBlock lets the install step degrade to on-disk files).
     */
    fun onConnectivityRetry() {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        scope.launch(Dispatchers.IO) {
            onGameStart(blocked.email, tolerateConnectivityBlock = true)
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
