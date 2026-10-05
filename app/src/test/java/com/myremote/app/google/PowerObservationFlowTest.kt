package com.myremote.app.google

import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeTvController
import com.myremote.app.domain.*
import com.myremote.app.google.protocol.ProtoWire
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

/** Real Start-frame decoder -> owned revision StateFlow -> Main-shaped collector/coordinator.
 * Only the physical power send is suspended by a test gate; no direct Boolean observation calls.
 */
class PowerObservationFlowTest {
    private class Fixture(scope: CoroutineScope) {
        val observations = GoogleTvPowerObservations()
        val connectionJob = Job()
        var owner = observations.begin(connectionJob)
        val sending = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val collectorGate = CompletableDeferred<Unit>()
        var holdCollector = false
        val events = mutableListOf<Boolean>()
        val coordinator = RemoteCoordinator(FakeTvController(), object : StreamerController {
            override val connectionState = ConnectionState.CONNECTED
            override suspend fun powerOn() { events += true; sending.complete(Unit); finish.await() }
            override suspend fun powerOff() { events += false; sending.complete(Unit); finish.await() }
            override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = Unit
        }, FakeSoundbarController(), InputSource.XIAOMI,
            observedStreamerPower = { observations.state.value })
        val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            observations.state.collect { observation ->
                if (holdCollector) collectorGate.await()
                observation?.let(coordinator::updateStreamerPower)
            }
        }
        suspend fun deliver(on: Boolean, token: GoogleTvPowerObservations.Owner = owner) {
            // A complete production Google command read loop parses an actual framed Start.
            val frame = ProtoWire.frame(ProtoWire.message(40, ProtoWire.integer(1, if (on) 1 else 0)))
            FrameSocket(frame).use { socket ->
                GoogleTvCommandSession(socket).run({}, { observations.observe(token, it) })
            }
        }
        suspend fun close() {
            collector.cancelAndJoin()
            observations.clear()
            connectionJob.cancel()
        }
    }

    private fun command(initial: Boolean, fresh: Boolean?, delayedCollector: Boolean = false,
        disconnectAfterReading: Boolean = false) = runBlocking {
        withTimeout(5_000) {
            val f = Fixture(this)
            try {
                f.deliver(initial); yield()
                assertEquals(initial, f.coordinator.state.streamerPowerOn)
                f.holdCollector = delayedCollector
                val command = async { f.coordinator.dispatch(RemoteAction.Power) }
                f.sending.await()
                if (fresh != null) f.deliver(fresh)
                if (disconnectAfterReading) f.observations.end(f.owner)
                f.finish.complete(Unit); command.await()
                assertEquals(fresh ?: !initial, f.coordinator.state.streamerPowerOn)
                assertEquals(listOf(!initial), f.events)
                f.collectorGate.complete(Unit); yield()
                assertEquals(fresh ?: !initial, f.coordinator.state.streamerPowerOn)
            } finally { f.close() }
        }
    }
    @Test fun equalOnDuringSleepWinsThroughStartFrameAndStateFlow() = command(true, true)
    @Test fun equalOffDuringWakeWinsThroughStartFrameAndStateFlow() = command(false, false)
    @Test fun changedOffDuringSleepStillWins() = command(true, false)
    @Test fun changedOnDuringWakeStillWins() = command(false, true)
    @Test fun noTelemetryDuringSleepAllowsOptimisticOff() = command(true, null)
    @Test fun noTelemetryDuringWakeAllowsOptimisticOn() = command(false, null)
    @Test fun equalReadingWinsEvenWhenMainCollectorIsStillSuspended() = command(true, true, delayedCollector = true)
    @Test fun disconnectDoesNotEraseFreshEqualObservationBeforeCompletion() =
        command(true, true, delayedCollector = true, disconnectAfterReading = true)

    @Test fun repeatedEqualStartFramesEachAdvanceOrdering() = runBlocking {
        val f = Fixture(this)
        try {
            repeat(8) { index ->
                f.deliver(true); yield()
                assertEquals((index + 1).toLong(), f.observations.state.value!!.revision)
                assertTrue(f.observations.state.value!!.on)
                assertTrue(f.coordinator.state.streamerPowerOn)
            }
        } finally { f.close() }
    }

    @Test fun supersededAttemptOnSameJobCannotPublishOrClearFreshObservation() = runBlocking {
        val f = Fixture(this)
        try {
            f.deliver(true); val old = f.owner
            f.owner = f.observations.begin(f.connectionJob) // Same retry Job, new socket attempt.
            f.deliver(false); yield()
            val command = async { f.coordinator.dispatch(RemoteAction.Power) }; f.sending.await()
            f.deliver(false) // Fresh equal Off must beat pending Wake.
            val latest = f.observations.state.value
            f.deliver(true, old); f.observations.end(old)
            assertEquals(latest, f.observations.state.value)
            f.finish.complete(Unit); command.await()
            assertFalse(f.coordinator.state.streamerPowerOn)
        } finally { f.close() }
    }

    @Test fun cancelledOwnerCannotPublishAnotherStartObservation() = runBlocking {
        val f = Fixture(this)
        try {
            f.deliver(true); val latest = f.observations.state.value
            f.connectionJob.cancel(); f.deliver(false)
            assertEquals(latest, f.observations.state.value)
        } finally { f.close() }
    }

    @Test fun delayedPreCommandCollectorCannotOverwriteNoTelemetryOptimism() = runBlocking {
        val f = Fixture(this)
        try {
            f.holdCollector = true
            f.deliver(true)
            val command = async { f.coordinator.dispatch(RemoteAction.Power) }; f.sending.await()
            f.finish.complete(Unit); command.await()
            assertFalse(f.coordinator.state.streamerPowerOn)
            f.collectorGate.complete(Unit); yield()
            assertFalse(f.coordinator.state.streamerPowerOn)
        } finally { f.close() }
    }

    @Test fun lgEqualConnectedAssignmentIsLifecycleStateNotFreshPowerTelemetry() = runBlocking {
        val send = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val connections = MutableStateFlow(ConnectionState.CONNECTED)
        val coordinator = RemoteCoordinator(object : TvController {
            override val connectionState = ConnectionState.CONNECTED
            override suspend fun powerOn() = Unit
            override suspend fun powerOff() { send.complete(Unit); finish.await() }
            override suspend fun switchInput(source: InputSource) = Unit
        }, com.myremote.app.data.FakeStreamerController(), FakeSoundbarController())
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            connections.collect { coordinator.updateTvConnection(it) }
        }
        try {
            val command = async { coordinator.dispatch(RemoteAction.TvPower) }; send.await()
            connections.value = ConnectionState.CONNECTED // Not an SSAP power report.
            finish.complete(Unit); command.await(); assertFalse(coordinator.state.tvPowerOn)
        } finally { collector.cancelAndJoin() }
    }

    private class FrameSocket(frame: ByteArray) : SSLSocket() {
        private val input = ByteArrayInputStream(frame)
        override fun getInputStream() = input
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getSupportedCipherSuites() = emptyArray<String>()
        override fun getEnabledCipherSuites() = emptyArray<String>()
        override fun setEnabledCipherSuites(suites: Array<out String>?) = Unit
        override fun getSupportedProtocols() = emptyArray<String>()
        override fun getEnabledProtocols() = emptyArray<String>()
        override fun setEnabledProtocols(protocols: Array<out String>?) = Unit
        override fun getSession(): SSLSession = error("TLS is not used by the framed read-loop fixture")
        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
        override fun startHandshake() = Unit
        override fun setUseClientMode(mode: Boolean) = Unit
        override fun getUseClientMode() = true
        override fun setNeedClientAuth(need: Boolean) = Unit
        override fun getNeedClientAuth() = false
        override fun setWantClientAuth(want: Boolean) = Unit
        override fun getWantClientAuth() = false
        override fun setEnableSessionCreation(flag: Boolean) = Unit
        override fun getEnableSessionCreation() = false
    }
}
