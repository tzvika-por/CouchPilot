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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Public Android bond/connection callbacks establish readiness, never API return booleans. */
class HidStreamerController internal constructor(
    private val factory: HidTransportFactory,
    private val scope: CoroutineScope,
    private val load: () -> HidHost?,
    private val save: (HidHost?) -> Unit,
) : StreamerController, AutoCloseable {
    private val mutableState = MutableStateFlow(if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED)
    val state = mutableState.asStateFlow()
    override val connectionState get() = mutableState.value
    private val mutableError = MutableStateFlow<FailureKind?>(null)
    val error = mutableError.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val registered = mutableReady.asStateFlow()
    private var job: Job? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var transport: HidTransport? = null
    @Volatile private var session: HidSession? = null

    fun select(host: HidHost) { pause(); save(host); retry() }
    fun retry() {
        if (job?.isActive == true) return
        val host = load() ?: run { mutableState.value = ConnectionState.NOT_CONFIGURED; return }
        val token = generation.incrementAndGet()
        job = scope.launch {
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                var opened: HidTransport? = null
                try {
                    mutableError.value = null
                    mutableState.value = ConnectionState.CONNECTING
                    opened = withTimeout(10_000) { factory.open(host) }
                    currentCoroutineContext().ensureActive()
                    transport = opened
                    mutableReady.value = true
                    mutableState.value = if (opened.bonded) ConnectionState.CONNECTING else ConnectionState.PAIRING
                    // First association waits for an ordinary TV-side accessory approval, not a retry loop.
                    val active = opened
                    withTimeout(if (active.bonded) 20_000 else 120_000) {
                        active.events.first { event ->
                            if (event == HidEvent.DISCONNECTED) throw DeviceFailure(active.failure ?: FailureKind.NETWORK, "Bluetooth host disconnected")
                            event == HidEvent.CONNECTED
                        }
                    }
                    session = HidSession(active::send)
                    mutableState.value = ConnectionState.CONNECTED
                    attempt = 0
                    com.myremote.app.diagnostics.RemoteDiagnostics.record("google", "bluetooth", "connected")
                    active.events.collect { event ->
                        if (event == HidEvent.DISCONNECTED) throw DeviceFailure(active.failure ?: FailureKind.NETWORK, "Bluetooth host disconnected")
                    }
                } catch (cancelled: CancellationException) {
                    if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                    if (token == generation.get()) mutableError.value = FailureKind.NETWORK
                } catch (error: Exception) { if (token == generation.get()) mutableError.value = failureKind(error) }
                finally {
                    if (token == generation.get()) {
                        session = null
                        transport = null
                        mutableReady.value = false
                    }
                    opened?.close()
                }
                if (token != generation.get()) return@launch
                mutableState.value = ConnectionState.DISCONNECTED
                if (mutableError.value in setOf(FailureKind.PERMISSION_DENIED, FailureKind.UNAVAILABLE) || runCatching { opened?.bonded }.getOrNull() != true) break
                delay(longArrayOf(3_000, 6_000, 12_000, 24_000, 30_000)[attempt++.coerceAtMost(4)])
            }
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
        catch (error: Exception) { transport?.close(); throw error }
    }
    fun pause() {
        generation.incrementAndGet()
        job?.cancel(); job = null
        session = null
        transport?.close(); transport = null
        mutableReady.value = false
        mutableState.value = if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED
    }
    fun forget() { pause(); save(null); mutableError.value = null; mutableState.value = ConnectionState.NOT_CONFIGURED }
    override fun close() { pause(); scope.cancel() }
}
