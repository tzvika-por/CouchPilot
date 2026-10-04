package com.myremote.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

enum class CommandDevice { TV, STREAMER, SOUNDBAR }

/** Main-dispatcher confined. One bounded worker per device; never replay an in-flight command.
 * Pending input switches are superseded by the latest choice. Other pending commands expire
 * instead of running after a reconnect. Cancellation also cancels the active operation, allowing
 * adapters to release pressed keys and close incomplete requests using their normal cleanup.
 */
class CommandScheduler(
    private val scope: CoroutineScope,
    private val target: (RemoteAction) -> CommandDevice,
    private val execute: suspend (RemoteAction, CommandDevice) -> Unit,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : AutoCloseable {
    private data class Pending(val action: RemoteAction, val at: Long)
    private class Lane {
        val pending = ArrayDeque<Pending>()
        val signal = Channel<Unit>(Channel.CONFLATED)
        var worker: Job? = null
        var generation = 0L
    }
    private val lanes = CommandDevice.entries.associateWith { Lane() }
    private val mutableBusy = MutableStateFlow(emptySet<CommandDevice>())
    val busy = mutableBusy.asStateFlow()
    private var closed = false

    fun submit(action: RemoteAction): Boolean {
        if (closed) return false
        val device = target(action) // Capture power target at tap time, not after another source switch.
        val lane = lanes.getValue(device)
        if (action is RemoteAction.SelectInput) lane.pending.removeAll { it.action is RemoteAction.SelectInput }
        lane.pending.removeAll { nowMillis() - it.at >= if (device == CommandDevice.TV) 5_000 else 1_500 }
        if (lane.pending.size >= 8) return false
        lane.pending.addLast(Pending(action, nowMillis()))
        if (lane.worker?.isActive != true) {
            val owner = ++lane.generation
            lane.worker = scope.launch(start = CoroutineStart.LAZY) {
                for (ignored in lane.signal) {
                    while (lane.generation == owner && isActive && lane.pending.isNotEmpty()) {
                        val next = lane.pending.removeFirst()
                        val lifetime = if (device == CommandDevice.TV) 5_000 else 1_500
                        if (nowMillis() - next.at >= lifetime) continue
                        mutableBusy.value += device
                        try { execute(next.action, device) }
                        finally { if (lane.generation == owner) mutableBusy.value -= device }
                    }
                }
            }.also { it.start() }
        }
        lane.signal.trySend(Unit)
        return true
    }

    fun sourceChanged(source: InputSource) {
        if (source == InputSource.XIAOMI) return
        // Keep explicit standby intentions; discard navigation whose screen is no longer active.
        lanes.getValue(CommandDevice.STREAMER).pending.removeAll {
            when (it.action) {
                is RemoteAction.Key, is RemoteAction.NumericKey, RemoteAction.ChannelUp,
                RemoteAction.ChannelDown, RemoteAction.LastChannel -> true
                else -> false
            }
        }
    }

    fun cancelDevice(device: CommandDevice) {
        val lane = lanes.getValue(device)
        lane.generation++
        lane.pending.clear()
        lane.worker?.cancel()
        lane.worker = null
        mutableBusy.value -= device
    }

    fun cancelAll() { CommandDevice.entries.forEach(::cancelDevice) }

    override fun close() {
        closed = true
        cancelAll()
        lanes.values.forEach { it.signal.close() }
    }
}
