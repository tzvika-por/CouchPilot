package com.myremote.app.domain

interface TvController {
    val connectionState: ConnectionState
    suspend fun switchInput(source: InputSource)
    suspend fun powerOn()
    suspend fun powerOff()
}

interface StreamerController {
    val connectionState: ConnectionState
    suspend fun powerOn()
    suspend fun powerOff()
    suspend fun sendKey(key: RemoteKey, pressKind: PressKind = PressKind.SHORT)
}

interface SoundbarController {
    val connectionState: ConnectionState
    suspend fun togglePower()
    suspend fun volumeUp()
    suspend fun volumeDown()
    suspend fun mute()
}
