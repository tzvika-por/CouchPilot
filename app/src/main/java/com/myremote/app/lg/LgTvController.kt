package com.myremote.app.lg

import android.content.Context
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import com.myremote.app.domain.TvController
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import com.myremote.app.diagnostics.RemoteDiagnostics
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Production webOS adapter; the selected device, grant revision and TLS pin outlive the ViewModel. */
class LgTvController internal constructor(
    private val store: LgPairingStore,
    private val transportFactory: LgTransportFactory,
    val discovery: LgDiscovery,
    private val scope: CoroutineScope,
    private val wake: suspend (LgDevice) -> Unit,
) : TvController, AutoCloseable {
    constructor(context: Context) : this(
        LgPairingStore(context), OkHttpLgTransportFactory(com.myremote.app.network.LanNetwork(context)), SsdpLgDiscovery(context.applicationContext),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { device -> withContext(Dispatchers.IO) { LgWakeOnLan.send(context.applicationContext, device.wakeMacs) } },
    )

    private val _state = MutableStateFlow(savedState())
    val state: StateFlow<ConnectionState> = _state
    override val connectionState: ConnectionState get() = _state.value
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var connectionJob: Job? = null
    @Volatile private var deliberateOff = false
    @Volatile private var session: LgSsapSession? = null
    @Volatile private var inputs: List<LgInput> = emptyList()

    private fun savedState(): ConnectionState {
        val saved = store.read()
        return when {
            saved?.authorizationNeedsRefresh == true -> ConnectionState.AUTHORIZATION_REQUIRED
            saved?.clientKey == null -> ConnectionState.NOT_CONFIGURED
            else -> ConnectionState.DISCONNECTED
        }
    }

    fun startDiscovery() {
        discovery.start()
        if (_state.value != ConnectionState.CONNECTED && _state.value != ConnectionState.AUTHORIZATION_REQUIRED) {
            _state.value = ConnectionState.DISCOVERING
        }
    }

    fun stopDiscovery() {
        discovery.stop()
        if (_state.value == ConnectionState.DISCOVERING) _state.value = savedState()
    }

    fun select(device: LgDevice) {
        stopDiscovery()
        // Device changes and grant writes must follow completion of the previous registration job.
        connectSelected(retry = false, selected = device)
    }

    fun connectStored() {
        if (store.read()?.clientKey != null) connectSelected(retry = true)
    }

    fun retry() {
        if (store.read() == null) {
            _state.value = ConnectionState.NOT_CONFIGURED
            return
        }
        connectSelected(retry = store.read()?.clientKey != null)
    }

    /** Explicit user action: retire the old grant, then request TV approval without its key. */
    fun refreshAuthorization() {
        stopDiscovery()
        connectSelected(retry = false, refreshAuthorization = true)
    }

    private fun connectSelected(retry: Boolean, refreshAuthorization: Boolean = false, selected: LgDevice? = null) {
        deliberateOff = false
        val previous = connectionJob
        previous?.cancel()
        session?.close()
        session = null
        inputs = emptyList()
        _error.value = null
        connectionJob = scope.launch {
            withContext(NonCancellable) { previous?.join() }
            currentCoroutineContext().ensureActive()
            try {
                if (selected != null) store.selectOrUpdate(selected)
                if (refreshAuthorization) store.clearAuthorization(requireRefresh = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not update LG configuration"
                _state.value = ConnectionState.ERROR
                RemoteDiagnostics.record("lg", "connection", "failed")
                return@launch
            }
            val saved = store.read() ?: run { _state.value = ConnectionState.NOT_CONFIGURED; return@launch }
            if (saved.authorizationNeedsRefresh) {
                _state.value = ConnectionState.AUTHORIZATION_REQUIRED
                return@launch
            }
            var failures = 0
            var pairing = saved
            while (isActive) {
                _state.value = if (pairing.clientKey == null) ConnectionState.PAIRING else ConnectionState.CONNECTING
                var current: LgSsapSession? = null
                var terminalFailure = false
                try {
                    val transport = transportFactory.connect(pairing.device.host, pairing.certificatePin)
                    current = LgSsapSession(transport, scope)
                    val registeredKey = current.register(pairing.clientKey, pairing.device.uuid) {
                        _state.value = ConnectionState.PAIRING
                    }
                    store.registered(registeredKey, current.certificatePin)
                    current.deviceUuid?.let { store.learnedIdentity(it, current.certificatePin) }
                    pairing = store.read() ?: throw IOException("LG configuration was removed")
                    session = current
                    // Endpoint denial must not discard a registration that still supports power off.
                    inputs = try { LgProtocol.inputs(current.request(LgProtocol.INPUT_LIST)) }
                    catch (error: LgAuthorizationException) {
                        RemoteDiagnostics.record("lg", "input_list", "denied", error.errorCode)
                        emptyList()
                    }
                    _error.value = null
                    _state.value = ConnectionState.CONNECTED
                    failures = 0
                    RemoteDiagnostics.record("lg", "connection", "connected")
                    current.awaitClosed()
                    throw IOException("LG connection closed")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (!isActive) break
                    RemoteDiagnostics.record("lg", "connection", "failed", (error as? LgAuthorizationException)?.errorCode)
                    terminalFailure = error is LgRegistrationException || error is LgAuthorizationException ||
                        (error is DeviceFailure && error.kind == FailureKind.SECURITY) ||
                        com.myremote.app.domain.isTlsIdentityFailure(error)
                    if (error is LgAuthorizationException) {
                        store.clearAuthorization()
                        _error.value = null
                        _state.value = ConnectionState.AUTHORIZATION_REQUIRED
                    } else {
                        _error.value = error.message ?: "LG connection failed"
                        _state.value = if (terminalFailure || (!retry && pairing.clientKey == null)) ConnectionState.ERROR
                            else ConnectionState.DISCONNECTED
                    }
                } finally {
                    if (session === current) session = null
                    inputs = emptyList()
                    current?.close()
                }
                if (deliberateOff || terminalFailure || (!retry && pairing.clientKey == null)) break
                failures++
                delay(LgReconnect.delayMillis(failures))
            }
        }
    }

    private suspend fun controlRequest(uri: String, payload: JSONObject? = null) {
        val active = session?.takeIf { _state.value == ConnectionState.CONNECTED }
            ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "LG TV is not connected")
        val operation = when (uri) {
            LgProtocol.SWITCH_INPUT -> "switch_input"
            LgProtocol.LAUNCH_INPUT -> "launch_input"
            LgProtocol.TURN_OFF -> "power_off"
            else -> "request"
        }
        try {
            active.request(uri, payload)
            RemoteDiagnostics.record("lg", operation, "accepted")
        } catch (error: LgAuthorizationException) {
            RemoteDiagnostics.record("lg", operation, "denied", error.errorCode)
            // A command's denial is a capability failure. Registration and other commands remain valid.
            throw error
        }
    }

    override suspend fun switchInput(source: InputSource) {
        val input = LgProtocol.matchingInput(source, inputs)
        try {
            controlRequest(LgProtocol.SWITCH_INPUT, JSONObject().put("inputId", input.id))
        } catch (denied: LgAuthorizationException) {
            val appId = input.appId ?: throw denied
            // Use only the TV's reported app ID; never invent an HDMI application ID or switch labels.
            controlRequest(LgProtocol.LAUNCH_INPUT, JSONObject().put("id", appId))
        }
    }

    override suspend fun powerOff() {
        controlRequest(LgProtocol.TURN_OFF)
        deliberateOff = true
        session?.close()
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun powerOn() {
        val device = store.read()?.device ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "Set up the LG TV first")
        if (device.wakeMacs.isEmpty() || device.wakeMacs.any { runCatching { LgWakeOnLan.packet(it) }.isFailure }) {
            RemoteDiagnostics.record("lg", "wake", "missing_configuration")
            throw DeviceFailure(FailureKind.WAKE_NOT_CONFIGURED, "LG wake address is missing or invalid")
        }
        _state.value = ConnectionState.CONNECTING
        var ownedJob: Job? = null
        try {
            wake(device)
            RemoteDiagnostics.record("lg", "wake", "sent")
            retry()
            ownedJob = connectionJob
            // UDP send is not wake proof. Keep reconnection bounded and cancel it on failure.
            withTimeout(45_000) {
                while (_state.value != ConnectionState.CONNECTED) {
                    if (_state.value == ConnectionState.AUTHORIZATION_REQUIRED)
                        throw DeviceFailure(FailureKind.PERMISSION_DENIED, "LG wake requires authorization")
                    if (_state.value == ConnectionState.ERROR)
                        throw DeviceFailure(FailureKind.WAKE_UNCONFIRMED, "LG wake connection failed")
                    delay(250)
                }
            }
            RemoteDiagnostics.record("lg", "wake", "connected")
        } catch (error: Exception) {
            if (connectionJob === ownedJob) {
                ownedJob?.cancel()
                session?.close()
                session = null
                inputs = emptyList()
                if (_state.value != ConnectionState.AUTHORIZATION_REQUIRED) _state.value = savedState()
            } else if (ownedJob == null && _state.value == ConnectionState.CONNECTING) {
                _state.value = savedState()
            }
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            RemoteDiagnostics.record("lg", "wake", "unconfirmed")
            if (error is DeviceFailure) throw error
            throw DeviceFailure(FailureKind.WAKE_UNCONFIRMED, "LG wake was not confirmed", error)
        }
    }

    fun forgetPairing() {
        val previous = connectionJob
        previous?.cancel()
        session?.close()
        session = null
        inputs = emptyList()
        connectionJob = scope.launch {
            withContext(NonCancellable) { previous?.join() }
            currentCoroutineContext().ensureActive()
            store.clear()
            _state.value = ConnectionState.NOT_CONFIGURED
            _error.value = null
        }
    }

    fun pause() {
        stopDiscovery()
        connectionJob?.cancel()
        session?.close(); session = null
        inputs = emptyList()
        if (_state.value != ConnectionState.AUTHORIZATION_REQUIRED) _state.value = savedState()
    }

    override fun close() {
        stopDiscovery()
        (discovery as? AutoCloseable)?.close()
        connectionJob?.cancel()
        session?.close()
        session = null
        scope.cancel()
    }
}

internal object LgReconnect {
    fun delayMillis(failures: Int): Long = when (failures) {
        1 -> 5_000
        2 -> 10_000
        3 -> 20_000
        4 -> 30_000
        else -> 60_000
    }
}
