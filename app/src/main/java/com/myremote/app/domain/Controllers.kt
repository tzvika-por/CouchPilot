package com.myremote.app.domain

interface TvController {
    val connectionState: ConnectionState
    suspend fun switchInput(source: InputSource)
    suspend fun powerOn()
    suspend fun powerOff()
}

interface StreamerController {
    /** Nonblocking recovery of an existing grant/bond after external wake; never pairs or sends power. */
    fun reconnectAfterWake() = Unit
    val connectionState: ConnectionState
    suspend fun powerOn()
    suspend fun powerOff()
    suspend fun sendKey(key: RemoteKey, pressKind: PressKind = PressKind.SHORT)
}

interface SoundbarController {
    /** Nonblocking recovery after optical wake; never sends a power toggle or creates a bond. */
    fun reconnectAfterWake() = Unit
    val connectionState: ConnectionState
    suspend fun togglePower()
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun mute()
}
