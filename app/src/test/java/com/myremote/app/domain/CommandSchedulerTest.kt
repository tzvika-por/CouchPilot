package com.myremote.app.domain

import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeStreamerController
import com.myremote.app.data.FakeTvController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CommandSchedulerTest {
    private val remote = RemoteCoordinator(FakeTvController(), FakeStreamerController(), FakeSoundbarController())

    @Test fun slowLgDoesNotBlockNavigationOrVolume() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<CommandDevice>()
        val queue = CommandScheduler(backgroundScope, remote::target, { _, device ->
            if (device == CommandDevice.TV) gate.await()
            events += device
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.Power); runCurrent()
        queue.submit(RemoteAction.Key(RemoteKey.HOME))
        queue.submit(RemoteAction.VolumeUp); runCurrent()
        assertEquals(setOf(CommandDevice.STREAMER, CommandDevice.SOUNDBAR), events.toSet())
        gate.complete(Unit); runCurrent()
        assertEquals(CommandDevice.TV, events.last())
        queue.close()
    }

    @Test fun sameDeviceMaintainsMacroAndTapOrdering() = runTest {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            if (action == RemoteAction.LastChannel) gate.await()
            events += action
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.LastChannel); queue.submit(RemoteAction.NumericKey(3)); runCurrent()
        assertTrue(events.isEmpty()); gate.complete(Unit); runCurrent()
        assertEquals(listOf(RemoteAction.LastChannel, RemoteAction.NumericKey(3)), events)
        queue.close()
    }

    @Test fun slowReconnectDropsExpiredNavigationAndAcceptsFreshIntent() = runTest {
        val gate = CompletableDeferred<Unit>(); val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            if (events.isEmpty()) { events += action; gate.await() } else events += action
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.Key(RemoteKey.HOME)); runCurrent()
        queue.submit(RemoteAction.Key(RemoteKey.RIGHT)); advanceTimeBy(2_000)
        queue.submit(RemoteAction.Key(RemoteKey.BACK)); gate.complete(Unit); runCurrent()
        assertEquals(listOf(RemoteAction.Key(RemoteKey.HOME), RemoteAction.Key(RemoteKey.BACK)), events)
        queue.close()
    }

    @Test fun pendingSourceSelectionUsesLatestChoice() = runTest {
        val gate = CompletableDeferred<Unit>(); val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            if (action == RemoteAction.Power) gate.await()
            events += action
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.Power); runCurrent()
        queue.submit(RemoteAction.SelectInput(InputSource.PS5))
        queue.submit(RemoteAction.SelectInput(InputSource.PC))
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(RemoteAction.Power, RemoteAction.SelectInput(InputSource.PC)), events)
        queue.close()
    }

    @Test fun cancellationStopsActiveAndPendingWorkAndAllowsNewSession() = runTest {
        var cancelled = false; val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            if (action == RemoteAction.VolumeUp) try { awaitCancellation() } finally { cancelled = true }
            else events += action
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.VolumeUp); queue.submit(RemoteAction.Mute); runCurrent()
        queue.cancelAll(); queue.submit(RemoteAction.VolumeDown); runCurrent()
        assertTrue(cancelled); assertEquals(listOf(RemoteAction.VolumeDown), events)
        assertTrue(queue.busy.value.isEmpty()); queue.close()
    }

    @Test fun rapidNavigationIsBoundedAndOrdered() = runTest {
        val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ -> events += action })
        val taps = (0..7).map { RemoteAction.NumericKey(it) }
        taps.forEach { assertTrue(queue.submit(it)) }
        assertFalse(queue.submit(RemoteAction.NumericKey(9))); runCurrent()
        assertEquals(taps, events); queue.close()
    }

    @Test fun rapidVolumeTapsAreOrderedWithoutBlockingOtherDevices() = runTest {
        val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ -> events += action })
        val taps = listOf(RemoteAction.VolumeUp, RemoteAction.VolumeUp, RemoteAction.Mute, RemoteAction.VolumeDown)
        taps.forEach { queue.submit(it) }; runCurrent()
        assertEquals(taps, events); queue.close(); assertFalse(queue.submit(RemoteAction.VolumeUp))
    }

    @Test fun nonCooperativeOldWorkerCannotConsumeReplacementQueue() = runTest {
        val old = CompletableDeferred<Unit>(); val fresh = CompletableDeferred<Unit>(); val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            when (action) {
                RemoteAction.VolumeUp -> kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { old.await() }
                RemoteAction.VolumeDown -> fresh.await()
                else -> Unit
            }
            events += action
        })
        queue.submit(RemoteAction.VolumeUp); runCurrent(); queue.cancelDevice(CommandDevice.SOUNDBAR)
        queue.submit(RemoteAction.VolumeDown); queue.submit(RemoteAction.Mute); runCurrent()
        old.complete(Unit); runCurrent()
        assertFalse(events.contains(RemoteAction.Mute))
        assertEquals(setOf(CommandDevice.SOUNDBAR), queue.busy.value)
        fresh.complete(Unit); runCurrent()
        assertEquals(listOf(RemoteAction.VolumeUp, RemoteAction.VolumeDown, RemoteAction.Mute), events); queue.close()
    }

    @Test fun tvWakeIsReachableWhenSavedXiaomiContextAndTvAreDisconnected() = runTest {
        val tv = FakeTvController(); val streamer = FakeStreamerController()
        val coordinator = RemoteCoordinator(tv, streamer, FakeSoundbarController(), InputSource.XIAOMI)
        coordinator.updateTvConnection(ConnectionState.DISCONNECTED)
        assertEquals(CommandDevice.TV, coordinator.target(RemoteAction.TvPower))
        coordinator.dispatch(RemoteAction.TvPower)
        assertEquals(listOf("power:on"), tv.events)
        assertTrue(streamer.powerEvents.isEmpty())
        assertEquals(InputSource.XIAOMI, coordinator.state.selectedInput)
    }

    @Test fun acceptedNonXiaomiContextDropsPendingKeysButRetainsExplicitStandby() = runTest {
        val gate = CompletableDeferred<Unit>(); val events = mutableListOf<RemoteAction>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, _ ->
            if (action == RemoteAction.Key(RemoteKey.HOME)) gate.await()
            events += action
        })
        queue.submit(RemoteAction.Key(RemoteKey.HOME)); runCurrent()
        queue.submit(RemoteAction.LastChannel); queue.submit(RemoteAction.NumericKey(4)); queue.submit(RemoteAction.StreamerOff)
        queue.sourceChanged(InputSource.PC); gate.complete(Unit); runCurrent()
        assertEquals(listOf(RemoteAction.Key(RemoteKey.HOME), RemoteAction.StreamerOff), events); queue.close()
    }

    @Test fun deviceReplacementCancelsOnlyItsOwnActiveAndPendingCommands() = runTest {
        var tvCancelled = false; var soundCancelled = false
        val gate = CompletableDeferred<Unit>()
        val queue = CommandScheduler(backgroundScope, remote::target, { _, device ->
            if (device == CommandDevice.TV) try { gate.await() } finally { tvCancelled = true }
            if (device == CommandDevice.SOUNDBAR) try { gate.await() } finally { soundCancelled = true }
        })
        queue.submit(RemoteAction.Power); queue.submit(RemoteAction.VolumeUp); runCurrent()
        queue.cancelDevice(CommandDevice.TV); runCurrent()
        assertTrue(tvCancelled); assertFalse(soundCancelled)
        assertEquals(setOf(CommandDevice.SOUNDBAR), queue.busy.value)
        gate.complete(Unit); runCurrent(); queue.close()
    }

    @Test fun savedSourceRestoresContextWithoutSendingAnInputCommand() {
        val tv = FakeTvController()
        val coordinator = RemoteCoordinator(tv, FakeStreamerController(), FakeSoundbarController(), InputSource.XIAOMI)
        assertEquals(InputSource.XIAOMI, coordinator.state.selectedInput)
        assertEquals(ActiveDevice.STREAMER, coordinator.state.activeDevice)
        assertTrue(tv.events.isEmpty())
    }

    @Test fun powerTargetIsCapturedBeforePendingSourceCompletes() = runTest {
        val gate = CompletableDeferred<Unit>(); val targets = mutableListOf<CommandDevice>()
        val queue = CommandScheduler(backgroundScope, remote::target, { action, device ->
            if (action is RemoteAction.SelectInput) { gate.await(); remote.dispatch(action) }
            else targets += device
        }, { testScheduler.currentTime })
        queue.submit(RemoteAction.SelectInput(InputSource.XIAOMI)); runCurrent()
        queue.submit(RemoteAction.Power); gate.complete(Unit); runCurrent()
        assertEquals(listOf(CommandDevice.TV), targets); queue.close()
    }

    @Test fun unrelatedSuccessCannotHideAnotherDevicesFailure() = runTest {
        val tv = object : TvController by FakeTvController() { override suspend fun powerOn() { error("synthetic failure") } }
        val coordinator = RemoteCoordinator(tv, FakeStreamerController(), FakeSoundbarController())
        coordinator.dispatch(RemoteAction.Power); coordinator.dispatch(RemoteAction.VolumeUp)
        assertEquals(CommandDevice.TV, coordinator.state.failureDevice)
        assertEquals("synthetic failure", coordinator.state.errorMessage)
    }

    @Test fun coordinatorUsesIndependentDeviceLocks() = runTest {
        val gate = CompletableDeferred<Unit>()
        val tv = object : TvController by FakeTvController() { override suspend fun powerOn() { gate.await() } }
        val soundbar = FakeSoundbarController()
        val coordinator = RemoteCoordinator(tv, FakeStreamerController(), soundbar)
        backgroundScope.launch { coordinator.dispatch(RemoteAction.Power) }
        runCurrent(); coordinator.dispatch(RemoteAction.VolumeUp)
        assertEquals(listOf("volume:up"), soundbar.events)
        gate.complete(Unit); runCurrent(); assertEquals(2, coordinator.state.actionCount)
    }
}
