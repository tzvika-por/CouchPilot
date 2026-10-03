package com.myremote.app.data

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import com.myremote.app.domain.PressKind
import com.myremote.app.domain.RemoteKey
import com.myremote.app.domain.SoundbarController
import com.myremote.app.domain.StreamerController
import com.myremote.app.domain.TvController

/** In-memory adapters for UI development. They never send network or Bluetooth traffic. */
class FakeTvController : TvController {
    override val connectionState = ConnectionState.SIMULATED
    val events = mutableListOf<String>()

    override suspend fun switchInput(source: InputSource) { events += "input:${source.webOsId}" }
    override suspend fun powerOn() { events += "power:on" }
    override suspend fun powerOff() { events += "power:off" }
}

class FakeStreamerController : StreamerController {
    override val connectionState = ConnectionState.SIMULATED
    val events = mutableListOf<Pair<RemoteKey, PressKind>>()
    val powerEvents = mutableListOf<String>()

    override suspend fun powerOn() { powerEvents += "on" }
    override suspend fun powerOff() { powerEvents += "off" }
    override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) { events += key to pressKind }
}

class FakeSoundbarController : SoundbarController {
    override val connectionState = ConnectionState.SIMULATED
    val events = mutableListOf<String>()

    override fun volumeUp() { events += "volume:up" }
    override fun volumeDown() { events += "volume:down" }
    override fun mute() { events += "mute" }
}
