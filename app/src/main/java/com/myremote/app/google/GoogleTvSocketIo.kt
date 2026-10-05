package com.myremote.app.google

import com.myremote.app.google.protocol.ProtoWire
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Cancellation closes the native socket, unblocking connect, TLS negotiation or frame reads. */
internal object GoogleTvSocketIo {
    suspend fun <T> work(operation: (own: (Socket) -> Unit) -> T): T = suspendCancellableCoroutine { continuation ->
        val guard = Any()
        var owned: Socket? = null
        fun closeOwned() { synchronized(guard) { owned?.let { runCatching { it.close() } }; owned = null } }
        val worker = CoroutineScope(Dispatchers.IO).launch {
            try {
                val value = operation { socket ->
                    synchronized(guard) {
                        if (!continuation.isActive) {
                            socket.close()
                            throw CancellationException("Socket operation cancelled")
                        }
                        owned = socket
                    }
                }
                continuation.resume(value) { _, _, _ -> closeOwned() }
            } catch (error: Throwable) {
                closeOwned()
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
        }
        continuation.invokeOnCancellation { closeOwned(); worker.cancel() }
    }

    suspend fun writeFrame(socket: Socket, payload: ByteArray, timeoutMillis: Long = 4_000) {
        com.myremote.app.network.OwnedSocketWrite.run(timeoutMillis, { socket.close() }) {
            ProtoWire.writeFrame(socket.outputStream, payload)
        }
    }

    suspend fun readFrame(socket: Socket): ByteArray? = work { own ->
        own(socket)
        ProtoWire.readFrame(socket.inputStream)
    }
}
