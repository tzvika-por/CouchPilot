package com.myremote.app.google.protocol

import com.myremote.app.domain.RemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal object RemoteProtocol {
    const val START_LONG = 1
    const val END_LONG = 2
    const val SHORT = 3

    fun key(code: Int, direction: Int): ByteArray = ProtoWire.message(10, ProtoWire.concat(
        ProtoWire.integer(1, code), ProtoWire.integer(2, direction),
    ))

    fun configure(serverFeatures: Int): ByteArray = ProtoWire.message(1, ProtoWire.concat(
        ProtoWire.integer(1, serverFeatures and 35),
        ProtoWire.message(2, ProtoWire.concat(
            ProtoWire.integer(3, 1), ProtoWire.string(4, "1"),
            ProtoWire.string(5, "com.myremote.app"), ProtoWire.string(6, "1.0"),
        )),
    ))

    fun active(mask: Int): ByteArray = ProtoWire.message(2, ProtoWire.integer(1, mask))
    fun pingResponse(value: Int): ByteArray = ProtoWire.message(9, ProtoWire.integer(1, value))

    fun parse(payload: ByteArray): RemoteMessage {
        val field = ProtoWire.fields(payload).firstOrNull { it.bytes != null } ?: return RemoteMessage.Unknown
        val nested = ProtoWire.fields(field.bytes!!)
        return when (field.number) {
            1 -> RemoteMessage.Configure((nested.number(1) ?: 0).toInt())
            2 -> RemoteMessage.SetActive((nested.number(1) ?: 0).toInt())
            3 -> RemoteMessage.Error
            8 -> RemoteMessage.Ping((nested.number(1) ?: 0).toInt())
            40 -> RemoteMessage.Start(nested.number(1) == 1L)
            else -> RemoteMessage.Unknown
        }
    }
}

internal sealed interface RemoteMessage {
    data class Configure(val features: Int) : RemoteMessage
    data class SetActive(val mask: Int) : RemoteMessage
    data class Ping(val value: Int) : RemoteMessage
    data class Start(val started: Boolean) : RemoteMessage
    data object Error : RemoteMessage
    data object Unknown : RemoteMessage
}

/** Pure command-session handshake so state and replies can be tested without sockets. */
internal class RemoteSessionHandshake {
    var ready = false
        private set
    private var features = 0

    fun accept(message: RemoteMessage): ByteArray? = when (message) {
        is RemoteMessage.Configure -> {
            features = message.features and 35
            require(features and 2 != 0) { "TV does not support key commands" }
            RemoteProtocol.configure(features)
        }
        is RemoteMessage.SetActive -> RemoteProtocol.active(features)
        is RemoteMessage.Ping -> RemoteProtocol.pingResponse(message.value)
        is RemoteMessage.Start -> { ready = true; null }
        RemoteMessage.Error -> error("TV rejected remote session")
        RemoteMessage.Unknown -> null
    }
}

internal object KeyMapping {
    fun androidCode(key: RemoteKey): Int = when (key) {
        RemoteKey.UP -> 19
        RemoteKey.DOWN -> 20
        RemoteKey.LEFT -> 21
        RemoteKey.RIGHT -> 22
        RemoteKey.CENTER -> 23
        RemoteKey.BACK -> 4
        RemoteKey.HOME -> 3
        RemoteKey.PLAY_PAUSE -> 85
        RemoteKey.REWIND -> 89
        RemoteKey.FAST_FORWARD -> 90
        RemoteKey.CHANNEL_UP -> 166
        RemoteKey.CHANNEL_DOWN -> 167
        RemoteKey.DIGIT_0 -> 7
        RemoteKey.DIGIT_1 -> 8
        RemoteKey.DIGIT_2 -> 9
        RemoteKey.DIGIT_3 -> 10
        RemoteKey.DIGIT_4 -> 11
        RemoteKey.DIGIT_5 -> 12
        RemoteKey.DIGIT_6 -> 13
        RemoteKey.DIGIT_7 -> 14
        RemoteKey.DIGIT_8 -> 15
        RemoteKey.DIGIT_9 -> 16
    }
}

/** START_LONG and END_LONG must stay on the same connection. */
internal class KeyInjector(
    private val send: suspend (ByteArray) -> Unit,
    private val hold: suspend (Long) -> Unit = { delay(it) },
    private val longHoldMillis: Long = 650,
) {
    suspend fun inject(code: Int, long: Boolean) {
        if (!long) {
            send(RemoteProtocol.key(code, RemoteProtocol.SHORT))
            return
        }
        send(RemoteProtocol.key(code, RemoteProtocol.START_LONG))
        try {
            hold(longHoldMillis)
        } finally {
            withContext(NonCancellable) { send(RemoteProtocol.key(code, RemoteProtocol.END_LONG)) }
        }
    }
}

internal object ReconnectPolicy {
    fun delayMillis(failures: Int): Long = (1_000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(30_000L)
}
