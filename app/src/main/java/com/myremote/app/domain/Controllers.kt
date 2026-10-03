package com.myremote.app.domain

interface TvController {
    val connectionState: ConnectionState
    fun switchInput(source: InputSource)
    fun powerOn()
    fun powerOff()
}

interface StreamerController {
    val connectionState: ConnectionState
    fun powerOn()
    fun powerOff()
    fun sendKey(key: RemoteKey, pressKind: PressKind = PressKind.SHORT)
}

interface SoundbarController {
    val connectionState: ConnectionState
    fun volumeUp()
    fun volumeDown()
    fun mute()
}
