package com.myremote.app.google

import android.content.Context
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.PressKind
import com.myremote.app.domain.RemoteKey
import com.myremote.app.domain.StreamerController
import com.myremote.app.google.protocol.KeyMapping
import com.myremote.app.google.protocol.PairingHandshake
import com.myremote.app.google.protocol.PairingProtocol
import com.myremote.app.google.protocol.ProtoWire
import com.myremote.app.google.protocol.ReconnectPolicy
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Production adapter. A single socket owns the read loop; writes are serialized. */
class GoogleTvStreamerController(context: Context) : StreamerController, AutoCloseable {
    private val appContext = context.applicationContext
    private val identity = AndroidClientIdentity()
    private val sockets: GoogleTvSocketFactory = AndroidGoogleTvSocketFactory(identity, com.myremote.app.network.LanNetwork(appContext))
    private val store = PairingStore(appContext)
    val discovery: GoogleTvDiscovery = NsdGoogleTvDiscovery(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var commandSession: GoogleTvCommandSession? = null
    private val _state = MutableStateFlow(if (store.saved() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state
    override val connectionState: ConnectionState get() = _state.value
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var connectionJob: Job? = null
    @Volatile private var activeSocket: SSLSocket? = null
    private val pairingMutex = Mutex()
    private var pairing: PairingSession? = null
    private val _powerState = MutableStateFlow<Boolean?>(null)
    val powerState: StateFlow<Boolean?> = _powerState

    fun startDiscovery() {
        discovery.start()
        if (_state.value != ConnectionState.CONNECTED) _state.value = ConnectionState.DISCOVERING
    }

    fun stopDiscovery() {
        discovery.stop()
        if (_state.value == ConnectionState.DISCOVERING) {
            _state.value = if (store.saved() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
        }
    }

    suspend fun beginPairing(device: GoogleTvDevice) = withContext(Dispatchers.IO) { pairingMutex.withLock {
        disconnect()
        stopDiscovery()
        pairing?.socket?.close()
        pairing = null
        _state.value = ConnectionState.PAIRING
        _error.value = null
        try {
            val socket = sockets.open(device, device.pairingPort, null)
            try {
                currentCoroutineContext().ensureActive()
                val handshake = PairingHandshake()
                ProtoWire.writeFrame(socket.outputStream, handshake.request("My Remote"))
                repeat(3) {
                    val reply = GoogleTvSocketIo.readFrame(socket) ?: error("TV closed pairing connection")
                    currentCoroutineContext().ensureActive()
                    handshake.accept(reply)?.let { next -> ProtoWire.writeFrame(socket.outputStream, next) }
                }
                check(handshake.step == PairingHandshake.Step.CODE_REQUIRED)
                pairing = PairingSession(device, socket, handshake)
                _state.value = ConnectionState.WAITING_FOR_CODE
            } catch (error: Exception) {
                socket.close()
                throw error
            }
        } catch (cancelled: CancellationException) {
            _state.value = if (store.saved() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
            throw cancelled
        } catch (error: Exception) {
            fail(error)
            throw error
        }
    } }

    suspend fun finishPairing(code: String) = withContext(Dispatchers.IO) { pairingMutex.withLock {
        val session = pairing ?: error("Start pairing first")
        try {
            val client = identity.certificate.publicKey as RSAPublicKey
            val serverCertificate = session.socket.session.peerCertificates.single() as java.security.cert.X509Certificate
            val server = serverCertificate.publicKey as RSAPublicKey
            val secret = PairingProtocol.secretHash(client, server, code.trim())
            ProtoWire.writeFrame(session.socket.outputStream, session.handshake.submitSecret(secret))
            val reply = GoogleTvSocketIo.readFrame(session.socket) ?: error("TV closed pairing connection")
            session.handshake.accept(reply)
            check(session.handshake.step == PairingHandshake.Step.COMPLETE)
            currentCoroutineContext().ensureActive()
            store.save(session.device, AndroidClientIdentity.certificatePin(serverCertificate),
                requireNotNull(session.socket.inetAddress.hostAddress))
            pairing = null
            session.socket.close()
            connectStored()
        } catch (cancelled: CancellationException) {
            pairing = null
            session.socket.close()
            throw cancelled
        } catch (error: Exception) {
            pairing = null
            session.socket.close()
            fail(error)
            throw error
        }
    } }

    fun connectStored() {
        val saved = store.saved() ?: run { _state.value = ConnectionState.NOT_CONFIGURED; return }
        disconnect()
        connectionJob = scope.launch {
            var failures = 0
            var device = saved.device
            while (isActive) {
                _state.value = ConnectionState.CONNECTING
                var socket: SSLSocket? = null
                try {
                    socket = sockets.open(device, device.port, saved.serverPin)
                    currentCoroutineContext().ensureActive()
                    val address = requireNotNull(socket.inetAddress.hostAddress)
                    store.rememberAddress(address)
                    device = device.copy(lastSuccessfulAddress = address)
                    activeSocket = socket
                    runConnection(socket) { failures = 0 }
                    if (isActive) throw IllegalStateException("TV closed the connection")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (!isActive) break
                    com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "connection", "failed")
                    _error.value = error.message ?: "Connection failed"
                    _state.value = ConnectionState.DISCONNECTED
                } finally {
                    socket?.close()
                    if (activeSocket === socket) activeSocket = null
                }
                failures++
                delay(ReconnectPolicy.delayMillis(failures))
            }
        }
    }

    fun retry() = connectStored()

    fun cancelPairing() {
        pairing?.socket?.close()
        pairing = null
        if (_state.value == ConnectionState.WAITING_FOR_CODE || _state.value == ConnectionState.PAIRING) {
            _state.value = if (store.saved() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
        }
    }

    fun forgetPairing() {
        disconnect()
        pairing?.socket?.close()
        pairing = null
        store.clear()
        _state.value = ConnectionState.NOT_CONFIGURED
        _error.value = null
    }

    private suspend fun runConnection(socket: SSLSocket, onReady: () -> Unit) {
        val session = GoogleTvCommandSession(socket)
        commandSession = session
        try {
            session.run(onReady = {
                if (_state.value != ConnectionState.CONNECTED)
                    com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "connection", "connected")
                onReady()
                _error.value = null
                _state.value = ConnectionState.CONNECTED
            }, onPowerState = { _powerState.value = it })
        } finally { if (commandSession === session) commandSession = null }
    }

    override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) {
        inject(KeyMapping.androidCode(key), pressKind == PressKind.LONG)
    }

    override suspend fun powerOn() = inject(224, false) // WAKEUP; wake from full power-off remains unproven.
    override suspend fun powerOff() = inject(223, false) // SLEEP.

    private suspend fun inject(code: Int, long: Boolean) {
        val session = commandSession?.takeIf { _state.value == ConnectionState.CONNECTED }
            ?: throw com.myremote.app.domain.DeviceFailure(com.myremote.app.domain.FailureKind.NOT_CONNECTED, "Xiaomi is not connected")
        session.inject(code, long)
    }

    private fun disconnect() {
        commandSession = null
        _powerState.value = null
        connectionJob?.cancel()
        connectionJob = null
        activeSocket?.close()
        activeSocket = null
    }

    private fun fail(error: Exception) {
        _error.value = error.message ?: "Pairing failed"
        _state.value = ConnectionState.ERROR
    }

    fun pause() {
        stopDiscovery()
        cancelPairing()
        disconnect()
        _state.value = if (store.saved() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
    }

    override fun close() {
        stopDiscovery()
        disconnect()
        pairing?.socket?.close()
        pairing = null
        scope.cancel()
    }

    private data class PairingSession(val device: GoogleTvDevice, val socket: SSLSocket, val handshake: PairingHandshake)
}
