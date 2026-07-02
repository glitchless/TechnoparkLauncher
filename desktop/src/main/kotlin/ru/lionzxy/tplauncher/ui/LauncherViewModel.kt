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
import ru.lionzxy.tplauncher.ui.state.ConnectivityBlockOrigin
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

    /**
     * Builds the [LauncherState.ConnectivityBlocked] for a detected WSAEACCES block. Runs the cheap,
     * unprivileged AV assessment (cached) to pick product-specific guidance (Dr.Web steps, …) and to
     * decide whether the Windows Firewall auto-fix is worth leading with. [origin] carries whether the
     * block struck before login or during launch so the panel's retry/fix routes correctly.
     */
    private fun connectivityBlockedState(email: String, origin: ConnectivityBlockOrigin): LauncherState.ConnectivityBlocked {
        val assessment = repairOrchestrator().assess()
        return LauncherState.ConnectivityBlocked(
            email = email,
            message = connectivityBlockedMessage(assessment.products, assessment.avClass),
            canFirewallFix = assessment.firewallFixFirst,
            origin = origin,
        )
    }

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
            if (current.canFirewallFix) onConnectivityFix(email, password) else onConnectivityRetry(email, password)
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
                // A firewall/AV block (WSAEACCES, e.g. Dr.Web) surfaces here wrapped in
                // YDServiceAuthenticationException's non-standard `thrown` field, NOT the JVM cause
                // chain. isPermissionDeniedSocket now descends into that field, so detect the block
                // FIRST and show the repair assistant (login-origin: its retry re-authenticates)
                // instead of the misleading generic "Failed to authenticate..." message.
                if (ConnectivityBlockClassifier.isPermissionDeniedSocket(exp)) {
                    Logger.e("Login", "Login blocked by firewall/AV (WSAEACCES): ${exp.thrown}", exp)
                    _state.value = connectivityBlockedState(email, ConnectivityBlockOrigin.LOGIN)
                    return@launch
                }
                // The real cause (HTTP status / server error body) is in thrown/reason, NOT the JVM
                // cause chain, so it must be logged explicitly — otherwise only the misleading generic
                // "Failed to authenticate..." message is visible (as in the GUI login-failure log).
                Logger.e("Login", "Authentication failed (serverReason=${exp.reason?.error}, httpCause=${exp.thrown})", exp)
                _state.value = LauncherState.InitialError(
                    exp.reason?.error ?: exp.localizedMessage ?: Strings.checkInternetConnection
                )
                return@launch
            } catch (ioExp: IOException) {
                Logger.e("Login", "Network error during login", ioExp)
                _state.value = if (ConnectivityBlockClassifier.isPermissionDeniedSocket(ioExp)) {
                    connectivityBlockedState(email, ConnectivityBlockOrigin.LOGIN)
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
                _state.value = connectivityBlockedState(email, ConnectivityBlockOrigin.LAUNCH)
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
     * [RepairOutcome.Repaired] retries the blocked step (re-authenticates for a login-origin block,
     * re-launches for a launch-origin one), [RepairOutcome.Cancelled] (declined UAC) returns to the
     * panel with a clear message, and [RepairOutcome.Guidance] (rules applied but still blocked)
     * returns to the panel with AV-specific guidance and no firewall button. [email]/[password] are
     * the current login-field values, used only when re-authenticating a login-origin block.
     */
    fun onConnectivityFix(email: String, password: String) {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        scope.launch(Dispatchers.IO) {
            _state.value = loadingStateFor(blocked)
            when (val outcome = repairOrchestrator().tryFirewallFix()) {
                is RepairOutcome.Repaired -> retryBlockedStep(blocked, email, password)

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
     * "Повторить" on the [LauncherState.ConnectivityBlocked] panel: retry the blocked step without
     * touching the firewall (e.g. after the user added an exception in their antivirus). A login-origin
     * block re-runs authentication with the current [email]/[password]; a launch-origin block re-launches
     * the already-installed pack via the offline fallback even if the network is still blocked
     * (tolerateConnectivityBlock lets the install step degrade to on-disk files). Routing a login-origin
     * block into the launch path would dereference the still-null session.
     */
    fun onConnectivityRetry(email: String, password: String) {
        val blocked = _state.value as? LauncherState.ConnectivityBlocked ?: return
        retryBlockedStep(blocked, email, password)
    }

    /** Re-drives whichever step the block interrupted: re-auth for [ConnectivityBlockOrigin.LOGIN], an
     *  offline-tolerant re-launch for [ConnectivityBlockOrigin.LAUNCH]. */
    private fun retryBlockedStep(blocked: LauncherState.ConnectivityBlocked, email: String, password: String) {
        when (blocked.origin) {
            // onLogin manages its own IO dispatch (and re-validates the fields).
            ConnectivityBlockOrigin.LOGIN -> onLogin(email, password)
            ConnectivityBlockOrigin.LAUNCH -> scope.launch(Dispatchers.IO) {
                onGameStart(blocked.email, tolerateConnectivityBlock = true)
            }
        }
    }

    /** Transient progress state shown while a firewall fix runs, matching the block's origin. */
    private fun loadingStateFor(blocked: LauncherState.ConnectivityBlocked): LauncherState =
        when (blocked.origin) {
            ConnectivityBlockOrigin.LOGIN -> LauncherState.LoginProgress
            ConnectivityBlockOrigin.LAUNCH -> LauncherState.GameLoading(blocked.email)
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
