package com.myremote.app.samsung

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal interface SamsungTransport : AutoCloseable {
    suspend fun send(bytes: ByteArray)
    suspend fun receive(): SamsungProtocol.Frame?
}

internal fun interface SamsungTransportFactory { suspend fun connect(address: String): SamsungTransport }

/** No request IDs exist on this protocol. One query at a time, matched by family/command. */
internal class SamsungSession(private val transport: SamsungTransport, scope: CoroutineScope) : AutoCloseable {
    private val mutex = Mutex()
    private val closed = CompletableDeferred<Unit>()
    private var pending: Pair<Int, CompletableDeferred<SamsungProtocol.Frame>>? = null
    private val reader: Job = scope.launch {
        try {
            while (true) {
                val frame = transport.receive() ?: throw IOException("Samsung connection closed")
                synchronized(this@SamsungSession) {
                    pending?.takeIf { frame.family == 11 && frame.command == it.first }?.second?.complete(frame)
                }
            }
        } catch (error: Exception) {
            synchronized(this@SamsungSession) { pending?.second?.completeExceptionally(error) }
        } finally { closed.complete(Unit) }
    }

    suspend fun initialize() = operation {
        transport.send(SamsungProtocol.start())
        verifyVolume()
    }

    suspend fun volumeUp() = volumeCommand(SamsungProtocol.volumeUp())
    suspend fun volumeDown() = volumeCommand(SamsungProtocol.volumeDown())

    private suspend fun volumeCommand(command: ByteArray) = operation {
        transport.send(command)
        // Status confirms a living Samsung control session, not that physical volume changed.
        verifyVolume()
    }

    suspend fun mute() = operation {
        transport.send(SamsungProtocol.mute())
        val response = query(116, SamsungProtocol.muteQuery())
        SamsungProtocol.muted(response) ?: throw IOException("Samsung mute status was invalid")
    }

    /** A toggle, never retried: standby may close the stream without an acknowledgement. */
    suspend fun togglePower() = operation {
        verifyVolume()
        transport.send(SamsungProtocol.powerToggle())
    }

    private suspend fun verifyVolume() {
        val response = query(127, SamsungProtocol.volumeQuery())
        if (SamsungProtocol.volume(response) == null) throw IOException("Samsung volume status was invalid")
    }

    private suspend fun query(command: Int, bytes: ByteArray): SamsungProtocol.Frame {
        val reply = CompletableDeferred<SamsungProtocol.Frame>()
        synchronized(this) { pending = command to reply }
        try {
            transport.send(bytes)
            return reply.await()
        } finally { synchronized(this) { pending = null }; reply.cancel() }
    }

    private suspend fun <T> operation(block: suspend () -> T): T = mutex.withLock {
        try {
            // The budget includes command writes, query writes and the status response.
            withTimeout(4_000) { block() }
        } catch (error: Exception) {
            // This protocol has no IDs. Timeout/cancellation must retire the connection,
            // otherwise its late response could be mistaken for a later command's status.
            close()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (error is TimeoutCancellationException)
                throw DeviceFailure(FailureKind.NETWORK, "Samsung operation timed out", error)
            throw error
        }
    }

    suspend fun awaitClosed() = closed.await()

    override fun close() {
        reader.cancel()
        synchronized(this) { pending?.second?.completeExceptionally(IOException("Samsung session closed")) }
        transport.close()
        closed.complete(Unit)
    }
}
