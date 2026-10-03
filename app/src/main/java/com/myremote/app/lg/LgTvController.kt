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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Production webOS adapter; the selected device, key and TLS pin outlive the ViewModel. */
class LgTvController(context: Context) : TvController, AutoCloseable {
    private val appContext = context.applicationContext
    private val store = LgPairingStore(appContext)
    private val transportFactory: LgTransportFactory = OkHttpLgTransportFactory()
    val discovery: LgDiscovery = SsdpLgDiscovery(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(
        if (store.read()?.clientKey == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED,
    )
    val state: StateFlow<ConnectionState> = _state
    override val connectionState: ConnectionState get() = _state.value
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var connectionJob: Job? = null
    @Volatile private var deliberateOff = false
    @Volatile private var session: LgSsapSession? = null
    @Volatile private var inputIds: Set<String> = emptySet()

    fun startDiscovery() {
        discovery.start()
        if (_state.value != ConnectionState.CONNECTED) _state.value = ConnectionState.DISCOVERING
    }

    fun stopDiscovery() {
        discovery.stop()
        if (_state.value == ConnectionState.DISCOVERING) {
            _state.value = if (store.read()?.clientKey == null) ConnectionState.NOT_CONFIGURED
                else ConnectionState.DISCONNECTED
        }
    }

    fun select(device: LgDevice) {
        stopDiscovery()
        val selected = LgInstallation.forSelectedDevice(device)
        val existing = store.read()
        if (existing?.device?.host != selected.host ||
            (existing.device.uuid != null && selected.uuid != null && existing.device.uuid != selected.uuid)) {
            store.select(selected)
        }
        connectSelected(retry = false)
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

    private fun connectSelected(retry: Boolean) {
        deliberateOff = false
        connectionJob?.cancel()
        session?.close()
        session = null
        inputIds = emptySet()
        _error.value = null
        val saved = store.read() ?: return
        connectionJob = scope.launch {
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
                    terminalFailure = error is LgRegistrationException
                    _error.value = error.message ?: "LG connection failed"
                    _state.value = if (terminalFailure || (!retry && pairing.clientKey == null)) ConnectionState.ERROR
                        else ConnectionState.DISCONNECTED
                } finally {
                    if (session === current) session = null
                    inputIds = emptySet()
                    current?.close()
                }
                if (deliberateOff) break
                if (terminalFailure) break
                if (!retry && pairing.clientKey == null) break
                failures++
                delay(LgReconnect.delayMillis(failures))
            }
        }
    }

    override suspend fun switchInput(source: InputSource) {
        val active = session?.takeIf { _state.value == ConnectionState.CONNECTED }
            ?: throw IOException("LG TV is not connected; set it up first")
        val id = LgProtocol.matchingInput(source, inputIds)
        active.request(LgProtocol.SWITCH_INPUT, JSONObject().put("inputId", id))
    }

    override suspend fun powerOff() {
        val active = session?.takeIf { _state.value == ConnectionState.CONNECTED }
            ?: throw IOException("LG TV is not connected")
        active.request(LgProtocol.TURN_OFF)
        deliberateOff = true
        active.close()
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun powerOn() = withContext(Dispatchers.IO) {
        val device = store.read()?.device ?: throw IOException("Set up the LG TV first")
        LgWakeOnLan.send(appContext, device.wakeMacs)
        retry()
    }

    fun forgetPairing() {
        connectionJob?.cancel()
        connectionJob = null
        session?.close()
        session = null
        inputIds = emptySet()
        store.clear()
        _state.value = ConnectionState.NOT_CONFIGURED
        _error.value = null
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
