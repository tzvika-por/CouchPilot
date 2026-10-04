package com.myremote.app.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Maps one UI intention to the correct device controller. Protocol details stay in adapters. */
class RemoteCoordinator(
    private val tv: TvController,
    private val streamer: StreamerController,
    private val soundbar: SoundbarController,
) {
    private val actionMutex = Mutex()
    var state = RemoteState(
        tvConnection = tv.connectionState,
        streamerConnection = streamer.connectionState,
        soundbarConnection = soundbar.connectionState,
    )
        private set

    fun updateStreamerConnection(connectionState: ConnectionState): RemoteState {
        state = state.copy(streamerConnection = connectionState)
        return state
    }

    fun updateTvConnection(connectionState: ConnectionState): RemoteState {
        state = state.copy(tvConnection = connectionState,
            tvPowerOn = if (connectionState == ConnectionState.CONNECTED) true else state.tvPowerOn)
        return state
    }

    fun updateSoundbarConnection(connectionState: ConnectionState): RemoteState {
        state = state.copy(soundbarConnection = connectionState)
        return state
    }

    fun updateStreamerPower(on: Boolean): RemoteState {
        state = state.copy(streamerPowerOn = on)
        return state
    }

    fun updateSoundbarMute(muted: Boolean?): RemoteState {
        state = state.copy(soundbarMuted = muted)
        return state
    }

    suspend fun dispatch(action: RemoteAction): RemoteState = actionMutex.withLock {
        try {
            execute(action)
            state = state.copy(actionCount = state.actionCount + 1, errorMessage = null, failure = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(
                tvConnection = if (tv.connectionState == ConnectionState.AUTHORIZATION_REQUIRED)
                    ConnectionState.AUTHORIZATION_REQUIRED else state.tvConnection,
                errorMessage = error.message ?: error.javaClass.simpleName,
                failure = failureKind(error),
            )
        }
        state
    }

    private suspend fun execute(action: RemoteAction) {
        when (action) {
            RemoteAction.Power -> toggleActivePower()
            RemoteAction.StreamerOff -> {
                streamer.powerOff()
                state = state.copy(streamerPowerOn = false)
            }
            RemoteAction.SoundbarPower -> soundbar.togglePower()
            is RemoteAction.SelectInput -> selectInput(action.source)
            RemoteAction.VolumeUp -> soundbar.volumeUp()
            RemoteAction.VolumeDown -> soundbar.volumeDown()
            RemoteAction.Mute -> soundbar.mute()
            is RemoteAction.Key -> streamer.sendKey(action.key)
            is RemoteAction.NumericKey -> {
                require(action.digit in 0..9) { "Digit must be between 0 and 9" }
                streamer.sendKey(RemoteKey.valueOf("DIGIT_${action.digit}"))
            }
            RemoteAction.ChannelUp -> streamer.sendKey(RemoteKey.CHANNEL_UP)
            RemoteAction.ChannelDown -> streamer.sendKey(RemoteKey.CHANNEL_DOWN)
            RemoteAction.LastChannel -> lastChannel()
        }
    }

    private suspend fun selectInput(source: InputSource) {
        tv.switchInput(source)
        state = state.copy(
            selectedInput = source,
            activeDevice = if (source == InputSource.XIAOMI) ActiveDevice.STREAMER else ActiveDevice.TV,
        )
    }

    private suspend fun toggleActivePower() {
        when (state.activeDevice) {
            ActiveDevice.TV -> {
                val wake = state.tvConnection != ConnectionState.CONNECTED || !state.tvPowerOn
                if (wake) tv.powerOn() else tv.powerOff()
                state = state.copy(tvPowerOn = wake)
            }
            ActiveDevice.STREAMER -> {
                if (state.streamerPowerOn) streamer.powerOff() else streamer.powerOn()
                state = state.copy(streamerPowerOn = !state.streamerPowerOn)
            }
        }
    }

    /** yes+ currently opens quick actions on long OK with Last Channel selected. */
    private suspend fun lastChannel() {
        streamer.sendKey(RemoteKey.CENTER, PressKind.LONG)
        streamer.sendKey(RemoteKey.CENTER, PressKind.SHORT)
    }
}
