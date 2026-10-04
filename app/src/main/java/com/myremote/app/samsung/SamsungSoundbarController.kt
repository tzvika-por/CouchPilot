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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Selected Android bond persists; credentials/bonding remain owned by Android. */
class SamsungSoundbarController internal constructor(
    private val factory: SamsungTransportFactory,
    private val scope: CoroutineScope,
    private val load: () -> String?,
    private val save: (String?) -> Unit,
) : SoundbarController, AutoCloseable {
    constructor(context: Context) : this(
        SamsungBluetooth(context.applicationContext).transportFactory,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE).getString("address", null) },
        { address -> check(context.getSharedPreferences("samsung_soundbar", Context.MODE_PRIVATE)
            .edit().putString("address", address).commit()) },
    )

    private val _state = MutableStateFlow(if (load() == null) ConnectionState.NOT_CONFIGURED else ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state
    override val connectionState get() = _state.value
    private val _error = MutableStateFlow<FailureKind?>(null)
    val error: StateFlow<FailureKind?> = _error
    private var job: Job? = null
    @Volatile private var session: SamsungSession? = null

    fun select(device: SamsungDevice) { save(device.address); retry() }

    fun retry() {
        disconnect()
        val address = load() ?: run { _state.value = ConnectionState.NOT_CONFIGURED; return }
        job = scope.launch {
            var failures = 0
            while (isActive) {
                _state.value = ConnectionState.CONNECTING
                var active: SamsungSession? = null
                try {
                    val transport = factory.connect(address)
                    // Publish before initialize so cancellation closes blocking Bluetooth reads.
                    active = SamsungSession(transport, scope)
                    if (!isActive) { active.close(); break }
                    session = active
                    active.initialize()
                    _error.value = null
                    _state.value = ConnectionState.CONNECTED
                    failures = 0
                    RemoteDiagnostics.record("samsung", "connection", "connected")
                    active.awaitClosed()
                    throw IOException("Samsung connection closed")
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    if (!isActive) break
                    _error.value = com.myremote.app.domain.failureKind(error)
                    _state.value = ConnectionState.DISCONNECTED
                    RemoteDiagnostics.record("samsung", "connection", "failed")
                    if (_error.value in setOf(FailureKind.PERMISSION_DENIED, FailureKind.UNAVAILABLE, FailureKind.NOT_CONNECTED)) break
                } finally {
                    active?.close()
                    if (session === active) session = null
                }
                delay(reconnectDelay(++failures))
            }
        }
    }

    private suspend fun command(operation: String, action: suspend (SamsungSession) -> Unit) {
        val active = session?.takeIf { connectionState == ConnectionState.CONNECTED }
            ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "Samsung soundbar is not connected")
        try { action(active); RemoteDiagnostics.record("samsung", operation, "status_received") }
        catch (error: Exception) {
            if (error !is CancellationException) {
                active.close()
                _state.value = ConnectionState.DISCONNECTED
                _error.value = com.myremote.app.domain.failureKind(error)
                RemoteDiagnostics.record("samsung", operation, "failed")
            }
            throw error
        }
    }
    override suspend fun volumeUp() = command("volume_up") { it.volumeUp() }
    override suspend fun volumeDown() = command("volume_down") { it.volumeDown() }
    override suspend fun mute() = command("mute") { it.mute() }

    fun disconnect() {
        job?.cancel(); job = null
        session?.close(); session = null
        if (load() != null) _state.value = ConnectionState.DISCONNECTED
    }
    fun forget() { disconnect(); save(null); _state.value = ConnectionState.NOT_CONFIGURED; _error.value = null }
    override fun close() { disconnect(); scope.cancel() }
    internal companion object { fun reconnectDelay(failures: Int): Long = (3_000L shl (failures - 1).coerceIn(0, 4)).coerceAtMost(30_000) }
}
