package com.myremote.app.lg

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal class LgRegistrationException(message: String) : IOException(message)

/** One registered WebSocket, with a single reader and ID-correlated command replies. */
internal class LgSsapSession(private val transport: LgTransport, private val scope: CoroutineScope) : AutoCloseable {
    private val requests = LgRequests()
    private val closed = CompletableDeferred<Unit>()
    private var reader: Job? = null

    val certificatePin: String get() = transport.certificatePin

    suspend fun register(clientKey: String?, onApprovalNeeded: () -> Unit): String {
        transport.send(LgProtocol.hello("myremote_hello"))
        // Older webOS implementations answer hello; registration also works if they do not.
        withTimeoutOrNull(2_500) {
            while (true) {
                val message = LgProtocol.decode(transport.receive() ?: throw IOException("LG closed before registration"))
                if (message.type == "hello") break
                if (message.type == "error") {
                    LgProtocol.authorizationFailure(message)?.let { throw it }
                    throw IOException(message.error ?: "LG rejected hello")
                }
            }
        }
        transport.send(LgProtocol.register("myremote_register", clientKey))
        val registeredKey = try { withTimeout(90_000) {
            while (true) {
                val message = LgProtocol.decode(transport.receive() ?: throw IOException("LG closed during pairing"))
                when (message.type) {
                    "registered" -> {
                        val key = LgProtocol.clientKey(message)
                            ?: throw LgRegistrationException("LG registration returned no client key")
                        return@withTimeout key
                    }
                    "response" -> if (message.id == "myremote_register" &&
                        message.payload?.optString("pairingType") == "PROMPT") onApprovalNeeded()
                    "error" -> {
                        LgProtocol.authorizationFailure(message)?.let { throw it }
                        throw LgRegistrationException(message.error ?: "LG registration was rejected")
                    }
                }
            }
            error("unreachable")
        } } catch (error: TimeoutCancellationException) {
            throw LgRegistrationException("LG approval timed out")
        }
        reader = scope.launch {
            try {
                while (true) {
                    val text = transport.receive() ?: throw IOException("LG WebSocket closed")
                    requests.complete(LgProtocol.decode(text))
                }
            } catch (error: Exception) {
                requests.failAll(error)
            } finally {
                closed.complete(Unit)
            }
        }
        return registeredKey
    }

    suspend fun request(uri: String, payload: JSONObject? = null): LgMessage {
        val (id, reply) = requests.open()
        try {
            transport.send(LgProtocol.request(id, uri, payload))
            return try {
                LgProtocol.requireSuccess(withTimeout(10_000) { reply.await() })
            } catch (error: TimeoutCancellationException) {
                throw IOException("LG request timed out", error)
            }
        } finally {
            requests.remove(id)
        }
    }

    suspend fun awaitClosed() = closed.await()

    override fun close() {
        reader?.cancel()
        requests.failAll(IOException("LG session closed"))
        transport.close()
        closed.complete(Unit)
    }
}
