package com.myremote.app.domain

import com.myremote.app.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PowerObservationTest {
    @Test fun offWithoutTelemetryBecomesOff() = runTest {
        val c = RemoteCoordinator(FakeTvController(), FakeStreamerController(), FakeSoundbarController(), InputSource.XIAOMI)
        c.dispatch(RemoteAction.Power); assertFalse(c.state.streamerPowerOn)
    }
    @Test fun onWithoutTelemetryBecomesOn() = runTest {
        val c = RemoteCoordinator(FakeTvController(), FakeStreamerController(), FakeSoundbarController(), InputSource.XIAOMI)
        c.updateStreamerPower(false); c.dispatch(RemoteAction.Power); assertTrue(c.state.streamerPowerOn)
    }
    private fun duringSend(initial: Boolean, fresh: Boolean, explicitOff: Boolean = false) = runTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val device = object : StreamerController {
            override val connectionState = ConnectionState.CONNECTED
            override suspend fun powerOn() { started.complete(Unit); finish.await() }
            override suspend fun powerOff() { started.complete(Unit); finish.await() }
            override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = Unit
        }
        val c = RemoteCoordinator(FakeTvController(), device, FakeSoundbarController(), InputSource.XIAOMI)
        c.updateStreamerPower(initial)
        val command = async { c.dispatch(if (explicitOff) RemoteAction.StreamerOff else RemoteAction.Power) }
        started.await(); c.updateStreamerPower(fresh); finish.complete(Unit); command.await()
        assertEquals(fresh, c.state.streamerPowerOn)
    }
    @Test fun offTelemetryDuringSleepIsNotInverted() = duringSend(true, false)
    @Test fun newerOnTelemetryDuringSleepWins() = duringSend(true, true)
    @Test fun newerOffTelemetryDuringWakeWins() = duringSend(false, false)
    @Test fun onTelemetryDuringWakeWins() = duringSend(false, true)
    @Test fun explicitOffAlsoRespectsNewTelemetry() = duringSend(true, true, true)
    @Test fun rapidSequentialPowerUsesIntendedState() = runTest {
        val s = FakeStreamerController(); val c = RemoteCoordinator(FakeTvController(), s, FakeSoundbarController(), InputSource.XIAOMI)
        repeat(10) { c.dispatch(RemoteAction.Power) }
        assertEquals(List(10) { if (it % 2 == 0) "off" else "on" }, s.powerEvents); assertTrue(c.state.streamerPowerOn)
    }
    @Test fun newerTvConnectedObservationWinsOverPendingOff() = runTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val tv = object : TvController {
            override val connectionState = ConnectionState.CONNECTED
            override suspend fun powerOn() = Unit
            override suspend fun powerOff() { started.complete(Unit); finish.await() }
            override suspend fun switchInput(source: InputSource) = Unit
        }
        val c = RemoteCoordinator(tv, FakeStreamerController(), FakeSoundbarController())
        val pending = async { c.dispatch(RemoteAction.TvPower) }; started.await()
        c.updateTvConnection(ConnectionState.CONNECTED); finish.complete(Unit); pending.await()
        assertTrue(c.state.tvPowerOn)
    }
}
