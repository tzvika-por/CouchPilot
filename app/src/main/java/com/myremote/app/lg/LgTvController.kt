package com.myremote.app.lg

import android.content.Context
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import com.myremote.app.domain.TvController
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
        LgPairingStore(context), OkHttpLgTransportFactory(), SsdpLgDiscovery(context.applicationContext),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { device -> LgWakeOnLan.send(context.applicationContext, device.wakeMacs) },
    )

    private val _state = MutableStateFlow(savedState())
    val state: StateFlow<ConnectionState> = _state
    override val connectionState: ConnectionState get() = _state.value
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var connectionJob: Job? = null
    @Volatile private var deliberateOff = false
    @Volatile private var session: LgSsapSession? = null
    @Volatile private var inputIds: Set<String> = emptySet()

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
        connectSelected(retry = false, selected = LgInstallation.forSelectedDevice(device))
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
        inputIds = emptySet()
        _error.value = null
        connectionJob = scope.launch {
            withContext(NonCancellable) { previous?.join() }
            currentCoroutineContext().ensureActive()
            if (selected != null) {
                val existing = store.read()
                if (existing?.device?.host != selected.host ||
                    (existing.device.uuid != null && selected.uuid != null && existing.device.uuid != selected.uuid)) {
                    store.select(selected)
                }
            }
            if (refreshAuthorization) store.clearAuthorization(requireRefresh = false)
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
                    val registeredKey = current.register(pairing.clientKey) {
                        _state.value = ConnectionState.PAIRING
                    }
                    store.registered(registeredKey, current.certificatePin)
                    pairing = pairing.copy(clientKey = registeredKey, certificatePin = current.certificatePin)
                    session = current
                    inputIds = LgProtocol.inputIds(current.request(LgProtocol.INPUT_LIST))
                    _error.value = null
                    _state.value = ConnectionState.CONNECTED
                    failures = 0
                    current.awaitClosed()
                    throw IOException("LG connection closed")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (!isActive) break
                    terminalFailure = error is LgRegistrationException || error is LgAuthorizationException
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
                    inputIds = emptySet()
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
            ?: throw IOException("LG TV is not connected; set it up first")
        try {
            active.request(uri, payload)
        } catch (error: LgAuthorizationException) {
            if (session === active) {
                store.clearAuthorization()
                _state.value = ConnectionState.AUTHORIZATION_REQUIRED
                _error.value = null
                connectionJob?.cancel()
                session = null
                inputIds = emptySet()
                active.close()
            }
            throw error
        }
    }

    override suspend fun switchInput(source: InputSource) {
        val id = LgProtocol.matchingInput(source, inputIds)
        controlRequest(LgProtocol.SWITCH_INPUT, JSONObject().put("inputId", id))
    }

    override suspend fun powerOff() {
        controlRequest(LgProtocol.TURN_OFF)
        deliberateOff = true
        session?.close()
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun powerOn() = withContext(Dispatchers.IO) {
        val device = store.read()?.device ?: throw IOException("Set up the LG TV first")
        wake(device)
        retry()
    }

    fun forgetPairing() {
        val previous = connectionJob
        previous?.cancel()
        session?.close()
        session = null
        inputIds = emptySet()
        connectionJob = scope.launch {
            withContext(NonCancellable) { previous?.join() }
            currentCoroutineContext().ensureActive()
            store.clear()
            _state.value = ConnectionState.NOT_CONFIGURED
            _error.value = null
        }
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
