package com.myremote.app.hid

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import com.myremote.app.domain.PressKind
import com.myremote.app.domain.RemoteKey
import com.myremote.app.domain.StreamerController
import com.myremote.app.domain.failureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

/** Public Android bond/connection callbacks establish readiness, never API return booleans. */
class HidStreamerController internal constructor(
    private val factory: HidTransportFactory,
    private val scope: CoroutineScope,
    private val load: () -> HidHost?,
    private val save: (HidHost?) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : StreamerController, AutoCloseable {
    private val mutableState = MutableStateFlow(if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED)
    val state = mutableState.asStateFlow()
    override val connectionState get() = mutableState.value
    private val mutableError = MutableStateFlow<FailureKind?>(null)
    val error = mutableError.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val registered = mutableReady.asStateFlow()
    private val mutablePairing = MutableStateFlow(false)
    val pairing = mutablePairing.asStateFlow()
    private val mutableBonded = MutableStateFlow(false)
    val bonded = mutableBonded.asStateFlow()
    private enum class Request { PAIR, RECONNECT }
    @Volatile private var pairRequest: Channel<Request>? = null
    private var job: Job? = null

    fun requestPairing() { pairRequest?.trySend(Request.PAIR) }
    private val generation = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var transport: HidTransport? = null
    @Volatile private var session: HidSession? = null

    override fun reconnectAfterWake() {
        if (load() == null || connectionState in setOf(ConnectionState.CONNECTED,
                ConnectionState.CONNECTING, ConnectionState.PAIRING)) return
        if (job?.isActive == true) {
            // Keep the registered HID profile. An automatic request may never create a bond.
            if (transport?.bonded == true && mutableState.compareAndSet(
                    ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)) {
                if (pairRequest?.trySend(Request.RECONNECT)?.isSuccess != true)
                    mutableState.compareAndSet(ConnectionState.CONNECTING, ConnectionState.DISCONNECTED)
            }
        } else retry() // Opening a saved profile connects only an existing native bond.
    }

    fun select(host: HidHost) { pause(); save(host); retry() }
    fun retry() {
        if (job?.isActive == true) {
            if (session == null && !mutablePairing.value) pairRequest?.trySend(Request.PAIR)
            return
        }
        val host = load() ?: run { mutableState.value = ConnectionState.NOT_CONFIGURED; return }
        val token = generation.incrementAndGet()
        job = scope.launch {
            var opened: HidTransport? = null
            val requests = Channel<Request>(Channel.CONFLATED)
            try {
                mutableError.value = null
                mutableState.value = ConnectionState.CONNECTING
                opened = withTimeout(10_000) { factory.open(host) }
                currentCoroutineContext().ensureActive()
                val active = opened
                transport = active
                pairRequest = requests
                mutableBonded.value = active.bonded
                mutableReady.value = true
                var incomingConnected = false
                if (!active.bonded) {
                    // First setup has no automatic bond request or pre-action deadline.
                    mutableState.value = ConnectionState.DISCONNECTED
                    coroutineScope {
                        val connected = async { awaitConnected(active) }
                        select<Unit> {
                            connected.onAwait { }
                            requests.onReceive { request ->
                                if (request == Request.PAIR) {
                                    mutablePairing.value = true
                                    mutableState.value = ConnectionState.PAIRING
                                    active.requestPairing()
                                    withTimeout(120_000) { connected.await() }
                                } else connected.await()
                            }
                        }
                    }
                    incomingConnected = true
                }
                var attempts = 0
                while (currentCoroutineContext().isActive) {
                    var connectedAt: Long? = null
                    try {
                        mutableState.value = ConnectionState.CONNECTING
                        if (!incomingConnected) withTimeout(20_000) { awaitConnected(active) }
                        incomingConnected = false
                        currentCoroutineContext().ensureActive()
                        lateinit var owner: HidSession
                        owner = HidSession { report ->
                            if (session !== owner) throw DeviceFailure(FailureKind.NOT_CONNECTED, "Bluetooth press belongs to a previous connection")
                            active.send(report)
                        }
                        session = owner
                        while (requests.tryReceive().isSuccess) { /* Discard requests completed by this connection. */ }
                        mutablePairing.value = false
                        mutableBonded.value = active.bonded
                        mutableError.value = null
                        mutableState.value = ConnectionState.CONNECTED
                        connectedAt = nowMillis()
                        com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "bluetooth", "connected")
                        active.events.first { it == HidEvent.DISCONNECTED }
                        throw DeviceFailure(active.failure ?: FailureKind.NETWORK, "Bluetooth host disconnected")
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                        mutableError.value = FailureKind.NETWORK
                    } catch (error: Exception) {
                        mutableError.value = failureKind(error)
                    } finally { if (token == generation.get()) session = null }
                    currentCoroutineContext().ensureActive()
                    if (token != generation.get()) return@launch
                    mutableState.value = ConnectionState.DISCONNECTED
                    mutablePairing.value = false
                    mutableBonded.value = active.bonded
                    if (!active.bonded || mutableError.value in setOf(FailureKind.PERMISSION_DENIED, FailureKind.UNAVAILABLE, FailureKind.SECURITY)) break
                    // A brief Connected callback must not reset the budget for a flapping link.
                    if (connectedAt?.let { nowMillis() - it >= 30_000 } == true) attempts = 0
                    if (attempts >= 3) {
                        com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "bluetooth", "retry_paused")
                        // Preserve SDP so a TV-initiated reconnect can recover without another bond.
                        incomingConnected = coroutineScope {
                            val incoming = async { awaitConnected(active, passive = true) }
                            select<Boolean> {
                                incoming.onAwait { true }
                                requests.onReceive { incoming.cancel(); false }
                            }
                        }
                        if (!incomingConnected) {
                            attempts = 0
                            active.reconnect()
                        }
                    } else {
                        val wait = longArrayOf(3_000, 6_000, 12_000)[attempts++]
                        com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "bluetooth", "retry_wait", attempts)
                        coroutineScope {
                            val timer = async { delay(wait) }
                            select<Unit> {
                                timer.onAwait { }
                                requests.onReceive { timer.cancel(); attempts = 0 }
                            }
                        }
                        active.reconnect()
                    }
                }
            } catch (cancelled: CancellationException) {
                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                if (token == generation.get()) mutableError.value = FailureKind.NETWORK
            } catch (error: Exception) {
                if (token == generation.get()) mutableError.value = failureKind(error)
            } finally {
                requests.close()
                opened?.close()
                if (token == generation.get()) {
                    session = null
                    transport = null
                    pairRequest = null
                    mutableReady.value = false
                    mutablePairing.value = false
                    mutableState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }
    private suspend fun awaitConnected(active: HidTransport, passive: Boolean = false) {
        active.events.first { event ->
            if (event == HidEvent.DISCONNECTED && (!passive || active.failure != null))
                throw DeviceFailure(active.failure ?: FailureKind.NETWORK, "Bluetooth host disconnected")
            event == HidEvent.CONNECTED
        }
    }

    override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = press(HidProtocol.key(key), pressKind)
    override suspend fun powerOn() = press(HidProtocol.power(true), PressKind.SHORT)
    override suspend fun powerOff() = press(HidProtocol.power(false), PressKind.SHORT)
    private suspend fun press(report: HidReport, kind: PressKind) {
        val current = session?.takeIf { connectionState == ConnectionState.CONNECTED }
            ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "Xiaomi Bluetooth is not connected")
        try { current.press(report, kind) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (session === current) {
                pause()
                mutableError.value = failureKind(error)
            }
            throw error
        }
    }
    fun pause() {
        generation.incrementAndGet()
        job?.cancel(); job = null
        session = null
        transport?.close(); transport = null
        mutableReady.value = false
        mutablePairing.value = false
        pairRequest?.close(); pairRequest = null
        mutableState.value = if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
    }
    fun forget() { pause(); save(null); mutableBonded.value = false; mutableError.value = null; mutableState.value = ConnectionState.NOT_CONFIGURED }
    override fun close() { pause(); scope.cancel() }
}
