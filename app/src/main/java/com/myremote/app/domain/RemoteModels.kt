package com.myremote.app.domain

enum class ConnectionState {
    NOT_CONFIGURED, DISCOVERING, PAIRING, WAITING_FOR_CODE,
    CONNECTING, CONNECTED, DISCONNECTED, ERROR, AUTHORIZATION_REQUIRED, SIMULATED,
}

enum class ActiveDevice { TV, STREAMER }

enum class InputSource(val webOsId: String) {
    PS5("HDMI_1"),
    MAC_MINI("HDMI_2"),
    XIAOMI("HDMI_3"),
    PC("HDMI_4"),
}

enum class RemoteKey {
    UP, DOWN, LEFT, RIGHT, CENTER, BACK, HOME, PLAY_PAUSE, REWIND, FAST_FORWARD,
    CHANNEL_UP, CHANNEL_DOWN,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
    DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
}

enum class PressKind { SHORT, LONG }

sealed interface RemoteAction {
    data object Power : RemoteAction
    data object WatchYesPlus : RemoteAction
    data class SelectInput(val source: InputSource) : RemoteAction
    data object VolumeUp : RemoteAction
    data object VolumeDown : RemoteAction
    data object Mute : RemoteAction
    data class Key(val key: RemoteKey) : RemoteAction
    data class NumericKey(val digit: Int) : RemoteAction
    data object ChannelUp : RemoteAction
    data object ChannelDown : RemoteAction
    data object LastChannel : RemoteAction
}

data class RemoteState(
    val activeDevice: ActiveDevice = ActiveDevice.TV,
    val selectedInput: InputSource? = null,
    val tvConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val streamerConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val soundbarConnection: ConnectionState = ConnectionState.DISCONNECTED,
    val tvPowerOn: Boolean = true,
    val streamerPowerOn: Boolean = true,
    val actionCount: Int = 0,
    val errorMessage: String? = null,
    val failure: FailureKind? = null,
)
