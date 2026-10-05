package com.myremote.app.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Maps one UI intention to the correct device controller. Protocol details stay in adapters. */
class RemoteCoordinator(
    private val tv: TvController,
    private val streamer: StreamerController,
    private val soundbar: SoundbarController,
    initialInput: InputSource? = null,
    private val observedStreamerPower: () -> StreamerPowerObservation? = { null },
) {
    private var appliedStreamerRevision = -1L
    private var streamerPowerObservation = 0L
    private var tvPowerObservation = 0L
    private val deviceMutexes = CommandDevice.entries.associateWith { Mutex() }
    var state = RemoteState(
        selectedInput = initialInput,
        activeDevice = if (initialInput == InputSource.XIAOMI) ActiveDevice.STREAMER else ActiveDevice.TV,
        tvConnection = tv.connectionState,
        streamerConnection = streamer.connectionState,
        soundbarConnection = soundbar.connectionState,
    )
        private set

    fun updateBusy(devices: Set<CommandDevice>): RemoteState {
        state = state.copy(busyDevices = devices)
        return state
    }

    fun updateStreamerConnection(connectionState: ConnectionState): RemoteState {
        state = state.copy(streamerConnection = connectionState)
        return state
    }

    // LG publishes connection lifecycle transitions, not periodic power readings.
    // An equal CONNECTED assignment carries no additional power authority.
    fun updateTvConnection(connectionState: ConnectionState): RemoteState {
        if (connectionState == ConnectionState.CONNECTED) tvPowerObservation++
        state = state.copy(tvConnection = connectionState,
            tvPowerOn = if (connectionState == ConnectionState.CONNECTED) true else state.tvPowerOn)
        return state
    }

    fun updateSoundbarConnection(connectionState: ConnectionState): RemoteState {
        state = state.copy(soundbarConnection = connectionState)
        return state
    }

    fun updateStreamerPower(observation: StreamerPowerObservation): RemoteState {
        // A delayed collector must not reapply a pre-command reading over optimism.
        if (observation.revision <= appliedStreamerRevision) return state
        appliedStreamerRevision = observation.revision
        return updateStreamerPower(observation.on)
    }

    fun updateStreamerPower(on: Boolean): RemoteState {
        streamerPowerObservation++
        state = state.copy(streamerPowerOn = on)
        return state
    }

    fun updateSoundbarMute(muted: Boolean?): RemoteState {
        state = state.copy(soundbarMuted = muted)
        return state
    }

    fun reportFailure(error: Exception): RemoteState {
        state = state.copy(errorMessage = error.message ?: error.javaClass.simpleName, failure = failureKind(error), failureDevice = null)
        return state
    }

    fun target(action: RemoteAction): CommandDevice = when (action) {
        RemoteAction.Power -> if (state.activeDevice == ActiveDevice.TV) CommandDevice.TV else CommandDevice.STREAMER
        RemoteAction.SoundbarPower, RemoteAction.VolumeUp, RemoteAction.VolumeDown, RemoteAction.Mute -> CommandDevice.SOUNDBAR
        RemoteAction.TvPower, is RemoteAction.SelectInput -> CommandDevice.TV
        else -> CommandDevice.STREAMER
    }

    suspend fun dispatch(action: RemoteAction, target: CommandDevice = target(action)): RemoteState = deviceMutexes.getValue(target).withLock {
        try {
            execute(action, target)
            currentCoroutineContext().ensureActive()
            state = state.copy(actionCount = state.actionCount + 1,
                errorMessage = if (state.failureDevice == null || state.failureDevice == target) null else state.errorMessage,
                failure = if (state.failureDevice == null || state.failureDevice == target) null else state.failure,
                failureDevice = if (state.failureDevice == target) null else state.failureDevice)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            state = state.copy(
                tvConnection = if (tv.connectionState == ConnectionState.AUTHORIZATION_REQUIRED)
                    ConnectionState.AUTHORIZATION_REQUIRED else state.tvConnection,
                errorMessage = error.message ?: error.javaClass.simpleName,
                failure = failureKind(error),
                failureDevice = target,
            )
        }
        state
    }

    private suspend fun execute(action: RemoteAction, target: CommandDevice) {
        when (action) {
            RemoteAction.Power -> toggleActivePower(target)
            RemoteAction.TvPower -> toggleActivePower(CommandDevice.TV)
            RemoteAction.StreamerOff -> {
                setStreamerPower(false)
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
        currentCoroutineContext().ensureActive()
        if (source == InputSource.XIAOMI) reconnectAfterWake()
        state = state.copy(
            selectedInput = source,
            activeDevice = if (source == InputSource.XIAOMI) ActiveDevice.STREAMER else ActiveDevice.TV,
        )
    }

    private fun reconnectAfterWake() {
        // Ancillary recovery must not turn an accepted TV command into a failed/replayed command.
        runCatching { streamer.reconnectAfterWake() }
        runCatching { soundbar.reconnectAfterWake() }
    }

    private suspend fun toggleActivePower(target: CommandDevice) {
        when (if (target == CommandDevice.TV) ActiveDevice.TV else ActiveDevice.STREAMER) {
            ActiveDevice.TV -> {
                val observation = tvPowerObservation
                val wake = state.tvConnection != ConnectionState.CONNECTED || !state.tvPowerOn
                if (wake) {
                    tv.powerOn()
                    currentCoroutineContext().ensureActive()
                    reconnectAfterWake()
                } else tv.powerOff()
                currentCoroutineContext().ensureActive()
                if (tvPowerObservation == observation) state = state.copy(tvPowerOn = wake)
            }
            ActiveDevice.STREAMER -> {
                observedStreamerPower()?.let(::updateStreamerPower)
                setStreamerPower(!state.streamerPowerOn)
            }
        }
    }

    private suspend fun setStreamerPower(desired: Boolean) {
        val before = observedStreamerPower()
        before?.let(::updateStreamerPower)
        val observation = streamerPowerObservation
        if (desired) streamer.powerOn() else streamer.powerOff()
        currentCoroutineContext().ensureActive()
        val latest = observedStreamerPower()
        if (latest != null && (before == null || latest.revision > before.revision)) {
            // Read the authoritative snapshot too: its Main collector may still be queued.
            updateStreamerPower(latest)
        } else if (streamerPowerObservation == observation) {
            state = state.copy(streamerPowerOn = desired)
        }
    }

    /** yes+ currently opens quick actions on long OK with Last Channel selected. */
    private suspend fun lastChannel() {
        streamer.sendKey(RemoteKey.CENTER, PressKind.LONG)
        currentCoroutineContext().ensureActive()
        streamer.sendKey(RemoteKey.CENTER, PressKind.SHORT)
    }
}
