package com.myremote.app.domain

import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeStreamerController
import com.myremote.app.data.FakeTvController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCoordinatorTest {
    private val tv = FakeTvController()
    private val streamer = FakeStreamerController()
    private val soundbar = FakeSoundbarController()
    private val remote = RemoteCoordinator(tv, streamer, soundbar)

    @Test fun lastChannelSendsLongCenterThenShortCenter() = runBlocking {
        remote.dispatch(RemoteAction.LastChannel)

        assertEquals(
            listOf(RemoteKey.CENTER to PressKind.LONG, RemoteKey.CENTER to PressKind.SHORT),
            streamer.events,
        )
        assertEquals(1, remote.state.actionCount)
    }

    @Test fun lastChannelStopsWhenLongPressFails() = runBlocking {
        val failingStreamer = object : StreamerController {
            override val connectionState = ConnectionState.CONNECTED
            var calls = 0
            override suspend fun powerOn() = Unit
            override suspend fun powerOff() = Unit
            override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) {
                calls++
                error("Long press failed")
            }
        }
        val remoteWithFailure = RemoteCoordinator(tv, failingStreamer, soundbar)

        remoteWithFailure.dispatch(RemoteAction.LastChannel)

        assertEquals(1, failingStreamer.calls)
        assertEquals(0, remoteWithFailure.state.actionCount)
        assertEquals("Long press failed", remoteWithFailure.state.errorMessage)
    }

    @Test fun selectingXiaomiInputActivatesStreamer() = runBlocking {
        val state = remote.dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))

        assertEquals(listOf("input:HDMI_3"), tv.events)
        assertEquals(InputSource.XIAOMI, state.selectedInput)
        assertEquals(ActiveDevice.STREAMER, state.activeDevice)
        assertTrue(streamer.events.isEmpty())
    }

    @Test fun everySourceRoutesThroughTvControllerWithItsHdmiId() = runBlocking {
        InputSource.entries.forEach { source -> remote.dispatch(RemoteAction.SelectInput(source)) }
        assertEquals(listOf("input:HDMI_1", "input:HDMI_2", "input:HDMI_3", "input:HDMI_4"), tv.events)
        assertEquals(InputSource.PC, remote.state.selectedInput)
    }

    @Test fun otherInputsSelectTvForPowerWhileNavigationStillTargetsStreamer() = runBlocking {
        remote.updateTvConnection(ConnectionState.CONNECTED)
        remote.dispatch(RemoteAction.SelectInput(InputSource.PC))
        remote.dispatch(RemoteAction.Power)
        remote.dispatch(RemoteAction.Key(RemoteKey.HOME))

        assertEquals(ActiveDevice.TV, remote.state.activeDevice)
        assertEquals(listOf("input:HDMI_4", "power:off"), tv.events)
        assertEquals(listOf(RemoteKey.HOME to PressKind.SHORT), streamer.events)
        assertFalse(remote.state.tvPowerOn)
    }

    @Test fun disconnectedTvPowerActionUsesWakeOnLanPath() = runBlocking {
        remote.updateTvConnection(ConnectionState.DISCONNECTED)
        remote.dispatch(RemoteAction.Power)
        assertEquals(listOf("power:on"), tv.events)
        assertTrue(remote.state.tvPowerOn)
    }

    @Test fun deniedInputRetainsSelectionAndReportsAuthorizationState() = runBlocking {
        val deniedTv = object : TvController {
            override var connectionState = ConnectionState.CONNECTED
            override suspend fun powerOn() = Unit
            override suspend fun powerOff() = Unit
            override suspend fun switchInput(source: InputSource) {
                assertEquals(InputSource.XIAOMI, source)
                connectionState = ConnectionState.AUTHORIZATION_REQUIRED
                throw java.io.IOException("LG authorization needs refresh")
            }
        }
        val state = RemoteCoordinator(deniedTv, streamer, soundbar).dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, state.tvConnection)
        assertEquals(null, state.selectedInput)
        assertEquals(ActiveDevice.TV, state.activeDevice)
        assertEquals(0, state.actionCount)
        assertTrue(streamer.events.isEmpty())
    }

    @Test fun soundControlsAlwaysTargetSoundbar() = runBlocking {
        remote.dispatch(RemoteAction.VolumeDown)
        remote.dispatch(RemoteAction.Mute)
        remote.dispatch(RemoteAction.VolumeUp)

        assertEquals(listOf("volume:down", "mute", "volume:up"), soundbar.events)
    }

    @Test fun dedicatedPowerTargetsDoNotDependOnSelectedInput() = runBlocking {
        remote.dispatch(RemoteAction.SelectInput(InputSource.MAC_MINI))
        remote.dispatch(RemoteAction.StreamerOff)
        remote.dispatch(RemoteAction.SoundbarPower)
        assertEquals(listOf("input:HDMI_2"), tv.events)
        assertEquals(listOf("off"), streamer.powerEvents)
        assertEquals(listOf("power:toggle"), soundbar.events)
        assertEquals(InputSource.MAC_MINI, remote.state.selectedInput)
        assertEquals(ActiveDevice.TV, remote.state.activeDevice)
        assertFalse(remote.state.streamerPowerOn)
    }

    @Test fun failedStandbyDoesNotInvertStreamerPowerOrChangeTv() = runBlocking {
        val unavailable = object : StreamerController {
            override val connectionState = ConnectionState.DISCONNECTED
            override suspend fun powerOn() = error("Unexpected wake")
            override suspend fun powerOff() = throw DeviceFailure(FailureKind.NOT_CONNECTED, "No Xiaomi channel")
            override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = Unit
        }
        val result = RemoteCoordinator(tv, unavailable, soundbar).dispatch(RemoteAction.StreamerOff)
        assertTrue(result.streamerPowerOn)
        assertEquals(FailureKind.NOT_CONNECTED, result.failure)
        assertEquals(0, result.actionCount)
        assertTrue(tv.events.isEmpty())
    }

    @Test fun numericDigitsAreValidatedAndMapped() = runBlocking {
        remote.dispatch(RemoteAction.NumericKey(1))
        remote.dispatch(RemoteAction.NumericKey(10))

        assertEquals(listOf(RemoteKey.DIGIT_1 to PressKind.SHORT), streamer.events)
        assertEquals(1, remote.state.actionCount)
        assertTrue(remote.state.errorMessage!!.contains("Digit must be"))
    }

    @Test fun acceptedTvWakeAndXiaomiInputResumeBothSavedControllersWithoutPowerCommands() = runBlocking {
        remote.updateTvConnection(ConnectionState.DISCONNECTED)
        remote.dispatch(RemoteAction.Power)
        assertEquals(1, streamer.wakeRecoveries)
        assertEquals(1, soundbar.wakeRecoveries)
        remote.dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))
        assertEquals(2, streamer.wakeRecoveries)
        assertEquals(2, soundbar.wakeRecoveries)
        assertTrue(streamer.powerEvents.isEmpty())
        assertTrue(soundbar.events.isEmpty())
        assertEquals(listOf("power:on", "input:HDMI_3"), tv.events)
    }

    @Test fun tvOffOtherInputsAndSoundbarOffDoNotResumeSleepingDevices() = runBlocking {
        remote.updateTvConnection(ConnectionState.CONNECTED)
        remote.dispatch(RemoteAction.Power)
        remote.dispatch(RemoteAction.SelectInput(InputSource.MAC_MINI))
        remote.dispatch(RemoteAction.SoundbarPower)
        remote.dispatch(RemoteAction.StreamerOff)
        assertEquals(0, streamer.wakeRecoveries)
        assertEquals(0, soundbar.wakeRecoveries)
    }

    @Test fun rejectedTvCommandsNeverTriggerAncillaryRecovery() = runBlocking {
        val failing = object : TvController {
            override val connectionState = ConnectionState.DISCONNECTED
            override suspend fun powerOn() { error("No TV wake") }
            override suspend fun powerOff() = Unit
            override suspend fun switchInput(source: InputSource) { error("No input change") }
        }
        val coordinator = RemoteCoordinator(failing, streamer, soundbar)
        coordinator.dispatch(RemoteAction.Power)
        coordinator.dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))
        assertEquals(0, streamer.wakeRecoveries)
        assertEquals(0, soundbar.wakeRecoveries)
        assertEquals(0, coordinator.state.actionCount)
    }

    @Test fun ancillaryRecoveryFailureCannotReplayOrRejectAcceptedInput() = runBlocking {
        val unavailable = object : StreamerController by streamer {
            override fun reconnectAfterWake() { error("Bluetooth unavailable") }
        }
        val state = RemoteCoordinator(tv, unavailable, soundbar)
            .dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))
        assertEquals(listOf("input:HDMI_3"), tv.events)
        assertEquals(1, soundbar.wakeRecoveries)
        assertEquals(InputSource.XIAOMI, state.selectedInput)
        assertEquals(1, state.actionCount)
        assertEquals(null, state.failure)
    }

}
