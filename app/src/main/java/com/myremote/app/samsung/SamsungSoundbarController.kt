package com.myremote.app.samsung

import android.content.Context
import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import com.myremote.app.domain.SoundbarController
import com.myremote.app.diagnostics.RemoteDiagnostics
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Selected Android bond persists; credentials/bonding remain owned by Android. */
class SamsungSoundbarController internal constructor(
    private val factory: SamsungTransportFactory,
    private val scope: CoroutineScope,
    private val load: () -> String?,
    private val save: (String?) -> Unit,
    private val loadSuspended: () -> Boolean = { false },
    private val saveSuspended: (Boolean) -> Unit = {},
) : SoundbarController, AutoCloseable {
    constructor(context: Context) : this(
        SamsungBluetooth(context.applicationContext).transportFactory,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE).getString("address", null) },
        { address -> check(context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE)
            .edit().putString("address", address).commit()) },
        { context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE).getBoolean("power_connection_suspended", false) },
        { suspended -> check(context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE)
            .edit().putBoolean("power_connection_suspended", suspended).commit()) },
    )

    private val _state = MutableStateFlow(if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state
    override val connectionState get() = _state.value
    private val _error = MutableStateFlow<FailureKind?>(null)
    val error: StateFlow<FailureKind?> = _error
    private val _muted = MutableStateFlow<Boolean?>(null)
    val muted: StateFlow<Boolean?> = _muted
    private val storageDispatcher = scope.coroutineContext[kotlin.coroutines.ContinuationInterceptor]
        as kotlinx.coroutines.CoroutineDispatcher
    private val ownership = Any()
    private var generation = 0L
    private var job: Job? = null
    private fun owns(id: Long) = synchronized(ownership) { generation == id }
    private fun publish(id: Long, change: () -> Unit): Boolean = synchronized(ownership) {
        if (generation != id) false else { change(); true }
    }
    @Volatile private var session: SamsungSession? = null

    @Volatile private var connectionSuspended = loadSuspended()

    fun select(device: SamsungDevice) { save(device.address); retry() }

    /** Foreground entry must not undo the user's power request, including after process restart. */
    fun connectStored() { if (!connectionSuspended) retry() }

    override fun reconnectAfterWake() {
        if (load() == null || connectionState in setOf(ConnectionState.CONNECTED, ConnectionState.CONNECTING)) return
        connect(retryBeforeConnected = false, initialAttemptLimit = 3)
    }

    fun retry() = connect(retryBeforeConnected = true)

    private fun connect(retryBeforeConnected: Boolean, initialAttemptLimit: Int = 1) = synchronized(ownership) {
        saveSuspended(false)
        connectionSuspended = false
        disconnect()
        val address = load() ?: run { _state.value = ConnectionState.NOT_CONFIGURED; return@synchronized }
        val owner = generation
        _error.value = null
        _state.value = ConnectionState.CONNECTING
        job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var failures = 0
            var canRetry = retryBeforeConnected
            while (isActive && owns(owner) && !connectionSuspended) {
                publish(owner) { _muted.value = null; _state.value = ConnectionState.CONNECTING }
                var active: SamsungSession? = null
                try {
                    suspend fun initialize() {
                        val transport = factory.connect(address)
                        // Publish before initialize so cancellation closes blocking Bluetooth reads.
                        val initialized = SamsungSession(transport, scope)
                        active = initialized
                        if (!isActive || !publish(owner) { session = initialized }) { initialized.close(); throw CancellationException("Superseded Samsung attempt") }
                        initialized.initialize()
                    }
                    if (initialAttemptLimit > 1 && !canRetry) {
                        kotlinx.coroutines.withTimeout(15_000) { initialize() }
                    } else initialize()
                    if (!isActive || !publish(owner) { _error.value = null; _state.value = ConnectionState.CONNECTED }) break
                    failures = 0
                    canRetry = true
                    RemoteDiagnostics.record("samsung", "connection", "connected")
                    active!!.awaitClosed()
                    throw IOException("Samsung connection closed")
                } catch (error: Exception) {
                    if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                    if (!isActive || !owns(owner)) break
                    if (connectionSuspended) { publish(owner) { _state.value = ConnectionState.DISCONNECTED }; break }
                    val kind = com.myremote.app.domain.failureKind(error)
                    if (!publish(owner) { _error.value = kind; _state.value = ConnectionState.DISCONNECTED }) break
                    RemoteDiagnostics.record("samsung", "connection", "failed")
                    val terminal = kind in setOf(FailureKind.PERMISSION_DENIED,
                        FailureKind.UNAVAILABLE, FailureKind.NOT_CONNECTED, FailureKind.SECURITY)
                    if (terminal || (!canRetry && failures + 1 >= initialAttemptLimit)) {
                        if (initialAttemptLimit > 1 && !canRetry) {
                            // A failed external-wake recovery must not enable background wake loops.
                            publish(owner) { saveSuspended(true); connectionSuspended = true }
                        }
                        break
                    }
                } finally {
                    active?.close()
                    publish(owner) { if (session === active) session = null }
                }
                delay(reconnectDelay(++failures))
            }
        }.also { it.start() }
    }

    private suspend fun command(operation: String, action: suspend (SamsungSession, Long) -> Unit) = kotlinx.coroutines.withContext(storageDispatcher) {
        // Optical Auto Power Link can wake the bar while post-Off reconnect remains suspended.
        // A new sound-button intention may restore control, but never replay a power toggle.
        if (session == null || connectionState != ConnectionState.CONNECTED) attemptWake()
        val owner = synchronized(ownership) { generation }
        val active = session?.takeIf { connectionState == ConnectionState.CONNECTED && owns(owner) }
            ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "Samsung soundbar is not connected")
        try { action(active, owner); RemoteDiagnostics.record("samsung", operation, "status_received") }
        catch (error: Exception) {
            if (error !is CancellationException) {
                active.close()
                publish(owner) {
                    _muted.value = null
                    _state.value = ConnectionState.DISCONNECTED
                    _error.value = com.myremote.app.domain.failureKind(error)
                }
                RemoteDiagnostics.record("samsung", operation, "failed")
            }
            throw error
        }
    }
    // A volume change may also clear mute. Without a fresh mute response the state is unknown.
    override suspend fun volumeUp() = command("volume_up") { active, owner -> publish(owner) { _muted.value = null }; active.volumeUp() }
    override suspend fun volumeDown() = command("volume_down") { active, owner -> publish(owner) { _muted.value = null }; active.volumeDown() }
    override suspend fun mute() = command("mute") { active, owner -> val value = active.mute(); publish(owner) { _muted.value = value } }

    override suspend fun togglePower() = kotlinx.coroutines.withContext(storageDispatcher) {
        val active = session?.takeIf { connectionState == ConnectionState.CONNECTED }
        if (active == null) {
            attemptWake()
            return@withContext
        }
        // Persist before writing: even a failed/cancelled write can have reached the device.
        // Never replay a toggle or reconnect automatically after an uncertain outcome.
        val owner = synchronized(ownership) {
            if (session !== active) throw CancellationException("Superseded Samsung command")
            saveSuspended(true)
            connectionSuspended = true
            generation
        }
        try {
            active.togglePower()
            RemoteDiagnostics.record("samsung", "power_toggle", "sent")
        } finally {
            synchronized(ownership) { if (owns(owner)) disconnect() else active.close() }
        }
    }

    /** A reconnect may itself wake Bluetooth Power On. Never follow it with an off toggle. */
    private suspend fun attemptWake() {
        if (load() == null) throw DeviceFailure(FailureKind.NOT_CONNECTED, "Set up the soundbar first")
        connect(retryBeforeConnected = false)
        val owner = synchronized(ownership) { generation }
        try {
            kotlinx.coroutines.withTimeout(15_000) {
                state.first {
                    it == ConnectionState.CONNECTED || (it == ConnectionState.DISCONNECTED && _error.value != null)
                }
            }
            if (!owns(owner)) throw CancellationException("Superseded Samsung wake")
            if (connectionState != ConnectionState.CONNECTED) {
                val kind = _error.value
                throw DeviceFailure(if (kind in setOf(FailureKind.PERMISSION_DENIED, FailureKind.UNAVAILABLE,
                    FailureKind.SECURITY, FailureKind.NOT_CONNECTED)) kind!! else FailureKind.WAKE_UNCONFIRMED,
                    "Soundbar wake was not confirmed")
            }
            RemoteDiagnostics.record("samsung", "wake", "connected")
        } catch (error: Exception) {
            publish(owner) {
                saveSuspended(true)
                connectionSuspended = true
                disconnect()
            }
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            RemoteDiagnostics.record("samsung", "wake", "unconfirmed")
            if (error is DeviceFailure) throw error
            throw DeviceFailure(FailureKind.WAKE_UNCONFIRMED, "Soundbar wake was not confirmed", error)
        }
    }

    fun disconnect() = synchronized(ownership) {
        generation++ // Invalidate BEFORE cancellation/close can deliver a late event.
        _muted.value = null
        job?.cancel(); job = null
        session?.close(); session = null
        if (load() != null) _state.value = ConnectionState.DISCONNECTED
    }
    fun forget() = synchronized(ownership) { disconnect(); save(null); _state.value = ConnectionState.NOT_CONFIGURED; _error.value = null }
    override fun close() { disconnect(); scope.cancel() }
    internal companion object { fun reconnectDelay(failures: Int): Long = (3_000L shl (failures - 1).coerceIn(0, 4)).coerceAtMost(30_000) }
}
