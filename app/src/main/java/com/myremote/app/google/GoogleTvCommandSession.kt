package com.myremote.app.google

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import com.myremote.app.google.protocol.KeyInjector
import com.myremote.app.google.protocol.ProtoWire
import com.myremote.app.google.protocol.RemoteMessage
import com.myremote.app.google.protocol.RemoteProtocol
import com.myremote.app.google.protocol.RemoteSessionHandshake
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shared by production and local TLS integration tests; one reader and serialized writes. */
internal class GoogleTvCommandSession(private val socket: SSLSocket) {
    private val writeMutex = Mutex()
    private val handshake = RemoteSessionHandshake()

    suspend fun run(onReady: () -> Unit, onPowerState: (Boolean) -> Unit = {}) {
        while (currentCoroutineContext().isActive && !socket.isClosed) {
            val payload = GoogleTvSocketIo.readFrame(socket) ?: return
            currentCoroutineContext().ensureActive()
            val message = RemoteProtocol.parse(payload)
            if (message is RemoteMessage.Start) onPowerState(message.started)
            handshake.accept(message)?.let { reply ->
                writeMutex.withLock { withContext(Dispatchers.IO) { ProtoWire.writeFrame(socket.outputStream, reply) } }
            }
            if (handshake.ready) onReady()
        }
    }

    suspend fun inject(code: Int, long: Boolean) {
        if (!handshake.ready || socket.isClosed) throw DeviceFailure(FailureKind.NOT_CONNECTED, "Xiaomi is not connected")
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                KeyInjector(send = { ProtoWire.writeFrame(socket.outputStream, it) }).inject(code, long)
            }
        }
    }
}
