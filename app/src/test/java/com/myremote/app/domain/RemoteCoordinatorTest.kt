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

    @Test fun watchYesSelectsXiaomiInputAndStreamer() = runBlocking {
        val state = remote.dispatch(RemoteAction.WatchYesPlus)

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

    @Test fun soundControlsAlwaysTargetSoundbar() = runBlocking {
        remote.dispatch(RemoteAction.VolumeDown)
        remote.dispatch(RemoteAction.Mute)
        remote.dispatch(RemoteAction.VolumeUp)

        assertEquals(listOf("volume:down", "mute", "volume:up"), soundbar.events)
    }

    @Test fun numericDigitsAreValidatedAndMapped() = runBlocking {
        remote.dispatch(RemoteAction.NumericKey(1))
        remote.dispatch(RemoteAction.NumericKey(10))

        assertEquals(listOf(RemoteKey.DIGIT_1 to PressKind.SHORT), streamer.events)
        assertEquals(1, remote.state.actionCount)
        assertTrue(remote.state.errorMessage!!.contains("Digit must be"))
    }
}
