package com.myremote.app.hid

import com.myremote.app.domain.PressKind
import com.myremote.app.domain.RemoteKey
import com.myremote.app.domain.StreamerController

enum class StreamerConnection { LAN, BLUETOOTH }

/** Setup chooses one transport. Never retry a possibly delivered command on another device/path. */
class StreamerRoute(
    private val lan: StreamerController,
    private val bluetooth: StreamerController,
    private val selected: () -> StreamerConnection,
) : StreamerController {
    private val current get() = if (selected() == StreamerConnection.LAN) lan else bluetooth
    override fun reconnectAfterWake() = current.reconnectAfterWake()
    override val connectionState get() = current.connectionState
    override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = current.sendKey(key, pressKind)
    override suspend fun powerOn() = current.powerOn()
    override suspend fun powerOff() = current.powerOff()
}
