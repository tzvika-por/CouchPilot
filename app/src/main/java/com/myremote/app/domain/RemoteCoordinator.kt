package com.myremote.app.domain

/** Maps one UI intention to the correct device controller. Protocol details stay in adapters. */
class RemoteCoordinator(
    private val tv: TvController,
    private val streamer: StreamerController,
    private val soundbar: SoundbarController,
) {
    var state = RemoteState(
        tvConnection = tv.connectionState,
        streamerConnection = streamer.connectionState,
        soundbarConnection = soundbar.connectionState,
    )
        private set

    fun dispatch(action: RemoteAction): RemoteState {
        try {
            execute(action)
            state = state.copy(actionCount = state.actionCount + 1, errorMessage = null)
        } catch (error: Exception) {
            state = state.copy(errorMessage = error.message ?: error.javaClass.simpleName)
        }
        return state
    }

    private fun execute(action: RemoteAction) {
        when (action) {
            RemoteAction.Power -> toggleActivePower()
            RemoteAction.WatchYesPlus -> selectInput(InputSource.XIAOMI)
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

    private fun selectInput(source: InputSource) {
        tv.switchInput(source)
        state = state.copy(
            selectedInput = source,
            activeDevice = if (source == InputSource.XIAOMI) ActiveDevice.STREAMER else ActiveDevice.TV,
        )
    }

    private fun toggleActivePower() {
        when (state.activeDevice) {
            ActiveDevice.TV -> {
                if (state.tvPowerOn) tv.powerOff() else tv.powerOn()
                state = state.copy(tvPowerOn = !state.tvPowerOn)
            }
            ActiveDevice.STREAMER -> {
                if (state.streamerPowerOn) streamer.powerOff() else streamer.powerOn()
                state = state.copy(streamerPowerOn = !state.streamerPowerOn)
            }
        }
    }

    /** yes+ currently opens quick actions on long OK with Last Channel selected. */
    private fun lastChannel() {
        streamer.sendKey(RemoteKey.CENTER, PressKind.LONG)
        streamer.sendKey(RemoteKey.CENTER, PressKind.SHORT)
    }
}
