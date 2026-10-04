package com.myremote.app.hid

import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeTvController
import com.myremote.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidControllerTest {
    private val host = HidHost("Synthetic TV", "02:00:00:00:00:01")
    private class Transport(override var bonded: Boolean = true) : HidTransport {
        val channel = Channel<HidEvent>(Channel.UNLIMITED)
        override val events = channel.receiveAsFlow()
        val reports = mutableListOf<HidReport>()
        var closed = false
        var pairingRequests = 0
        var reconnects = 0
        var onReconnect: () -> Unit = {}
        override fun reconnect() { reconnects++; onReconnect() }
        override fun requestPairing() { pairingRequests++ }
        override fun send(report: HidReport) { check(!closed); reports += report }
        override fun close() { closed = true; channel.close() }
    }
    @Test fun callbackEstablishesReadinessSelectionPersistsAndLifecycleReusesBond() = runTest {
        var saved: HidHost? = null
        val transports = mutableListOf<Transport>()
        val factory = HidTransportFactory { assertEquals(host, it); Transport().also(transports::add) }
        val controller = HidStreamerController(factory, backgroundScope, { saved }, { saved = it })
        controller.select(host); runCurrent()
        assertEquals(host, saved)
        assertEquals(ConnectionState.CONNECTING, controller.connectionState)
        assertTrue(controller.registered.value)
        transports[0].channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        controller.pause(); runCurrent(); assertTrue(transports[0].closed)
        assertFalse(controller.registered.value)
        controller.retry(); runCurrent(); assertEquals(2, transports.size)
        transports[1].channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        controller.forget(); runCurrent(); assertNull(saved)
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
    }
    @Test fun unbondedAssociationTimesOutWithoutAutomaticPairingLoop() = runTest {
        var count = 0
        val transport = Transport(false)
        val controller = HidStreamerController(HidTransportFactory { count++; transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertTrue(controller.registered.value)
        advanceTimeBy(240_000); runCurrent()
        assertTrue(controller.registered.value)
        assertFalse(transport.closed)
        controller.requestPairing(); controller.requestPairing(); runCurrent()
        assertEquals(1, transport.pairingRequests)
        assertTrue(controller.pairing.value)
        assertEquals(ConnectionState.PAIRING, controller.connectionState)
        advanceTimeBy(120_001); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertEquals(FailureKind.NETWORK, controller.error.value)
        assertTrue(transport.closed)
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, count)
    }
    @Test fun initialPairingRequiresExplicitActionAndOnlyConnectionCallbackMakesItReady() = runTest {
        val transport = Transport(false)
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent()
        assertEquals(0, transport.pairingRequests)
        controller.requestPairing(); runCurrent()
        assertEquals(ConnectionState.PAIRING, controller.connectionState)
        assertTrue(controller.pairing.value)
        transport.bonded = true
        assertEquals(ConnectionState.PAIRING, controller.connectionState)
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertFalse(controller.pairing.value)
        controller.pause(); runCurrent(); assertTrue(transport.closed)
    }
    @Test fun leavingDuringPairingClosesNativeOwnershipAndDiscardsOldPairRequests() = runTest {
        val transports = mutableListOf<Transport>()
        val controller = HidStreamerController(HidTransportFactory { Transport(false).also(transports::add) }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); controller.requestPairing(); runCurrent()
        assertTrue(controller.pairing.value)
        controller.pause(); runCurrent()
        assertTrue(transports[0].closed); assertFalse(controller.pairing.value)
        controller.requestPairing()
        controller.retry(); runCurrent()
        assertEquals(0, transports[1].pairingRequests)
        assertTrue(controller.registered.value)
        assertFalse(controller.pairing.value)
    }
    @Test fun bondedReconnectKeepsRegistrationAndPermissionFailureStops() = runTest {
        var opens = 0
        val transport = Transport().apply { onReconnect = { throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Revoked") } }
        val controller = HidStreamerController(HidTransportFactory { opens++; transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); transport.channel.send(HidEvent.CONNECTED); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        assertFalse(transport.closed); assertTrue(controller.registered.value)
        advanceTimeBy(2_999); runCurrent(); assertEquals(0, transport.reconnects)
        advanceTimeBy(1); runCurrent(); assertEquals(1, transport.reconnects)
        assertTrue(transport.closed)
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, opens)
        assertEquals(FailureKind.PERMISSION_DENIED, controller.error.value)
    }
    @Test fun twoSecondConnectionsCannotResetBackoffAndTvCanRecoverAfterBudgetIsExhausted() = runTest {
        var opens = 0
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { opens++; transport }, backgroundScope, { host }, {}, { testScheduler.currentTime })
        controller.retry(); runCurrent()
        for ((index, wait) in listOf(3_000L, 6_000L, 12_000L).withIndex()) {
            transport.channel.send(HidEvent.CONNECTED); runCurrent()
            advanceTimeBy(2_000); runCurrent()
            transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
            assertFalse(transport.closed)
            advanceTimeBy(wait - 1); runCurrent(); assertEquals(index, transport.reconnects)
            advanceTimeBy(1); runCurrent(); assertEquals(index + 1, transport.reconnects)
        }
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        advanceTimeBy(2_000); runCurrent(); transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(3, transport.reconnects); assertEquals(1, opens)
        assertTrue(controller.registered.value); assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertEquals(1, opens); assertEquals(0, transport.pairingRequests)
        // Another brief TV-originated connection must not restart automatic attempts.
        advanceTimeBy(2_000); runCurrent(); transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        advanceTimeBy(120_000); runCurrent(); assertEquals(3, transport.reconnects)
        assertTrue(controller.registered.value)
    }
    @Test fun stableConnectionResetsBackoffAndForegroundCleanupStillClosesEverything() = runTest {
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {}, { testScheduler.currentTime })
        controller.retry(); runCurrent(); transport.channel.send(HidEvent.CONNECTED); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent(); advanceTimeBy(3_000); runCurrent()
        transport.channel.send(HidEvent.CONNECTED); runCurrent(); advanceTimeBy(30_000); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        advanceTimeBy(2_999); runCurrent(); assertEquals(1, transport.reconnects)
        advanceTimeBy(1); runCurrent(); assertEquals(2, transport.reconnects)
        controller.pause(); runCurrent(); assertTrue(transport.closed)
        assertFalse(controller.registered.value)
    }
    @Test fun manualRetryRestartsExhaustedBudgetWithoutRePairingOrReplacingSdp() = runTest {
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent()
        // Each connection attempt times out; no TV callbacks arrive.
        advanceTimeBy(101_001); runCurrent()
        assertEquals(3, transport.reconnects)
        assertTrue(controller.registered.value)
        controller.retry(); runCurrent(); assertEquals(4, transport.reconnects)
        assertEquals(0, transport.pairingRequests)
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
    }
    @Test fun oldPressCannotReleaseAReplacementSessionOrCloseItsRegistration() = runTest {
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); advanceTimeBy(101_001); runCurrent()
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        val old = async { runCatching { controller.sendKey(RemoteKey.CENTER, PressKind.LONG) } }
        runCurrent(); advanceTimeBy(300); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        val replacement = async { controller.sendKey(RemoteKey.RIGHT, PressKind.LONG) }
        runCurrent(); advanceTimeBy(351); runCurrent()
        assertTrue(old.await().isFailure)
        assertEquals(2, transport.reports.size)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertTrue(controller.registered.value); assertFalse(transport.closed)
        advanceTimeBy(300); runCurrent(); replacement.await()
        assertEquals(3, transport.reports.size)
        assertArrayEquals(byteArrayOf(0x45, 0), transport.reports[1].bytes)
        assertArrayEquals(byteArrayOf(0, 0), transport.reports[2].bytes)
    }
    @Test fun replacingSelectionCannotBeClobberedByCancelledSessionCleanup() = runTest {
        val transports = mutableListOf<Transport>(); var saved = host
        val controller = HidStreamerController(HidTransportFactory { Transport().also(transports::add) }, backgroundScope, { saved }, { saved = requireNotNull(it) })
        controller.retry(); runCurrent()
        transports[0].channel.send(HidEvent.CONNECTED); runCurrent()
        controller.select(host.copy(address = "02:00:00:00:00:02")); runCurrent()
        transports[1].channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertTrue(controller.registered.value)
        assertTrue(transports[0].closed)
        controller.pause(); runCurrent()
    }
    @Test fun longPressSerializesAndCancellationReleasesKey() = runTest {
        val sent = mutableListOf<Pair<Long, HidReport>>()
        val session = HidSession { sent += testScheduler.currentTime to it }
        val long = launch { session.press(HidProtocol.key(RemoteKey.CENTER), PressKind.LONG) }
        runCurrent()
        val next = launch { session.press(HidProtocol.key(RemoteKey.RIGHT), PressKind.SHORT) }
        advanceTimeBy(300); runCurrent(); assertEquals(1, sent.size)
        long.cancelAndJoin(); runCurrent()
        assertArrayEquals(byteArrayOf(0, 0), sent[1].second.bytes)
        assertArrayEquals(byteArrayOf(0x45, 0), sent[2].second.bytes)
        advanceTimeBy(61); runCurrent(); next.join()
        assertEquals(4, sent.size)
    }
    @Test fun domainLastChannelAndEveryKeyRouteThroughSelectedAdapter() = runTest {
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); transport.channel.send(HidEvent.CONNECTED); runCurrent()
        val lan = object : StreamerController {
            override val connectionState = ConnectionState.DISCONNECTED
            override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) { fail("Wrong transport") }
            override suspend fun powerOn() { fail("Wrong transport") }
            override suspend fun powerOff() { fail("Wrong transport") }
        }
        val route = StreamerRoute(lan, controller) { StreamerConnection.BLUETOOTH }
        val coordinator = RemoteCoordinator(FakeTvController(), route, FakeSoundbarController())
        val job = launch { coordinator.dispatch(RemoteAction.LastChannel) }
        runCurrent(); advanceTimeBy(651); runCurrent(); advanceTimeBy(61); runCurrent(); job.join()
        assertEquals(4, transport.reports.size)
        assertArrayEquals(byteArrayOf(0x41, 0), transport.reports[0].bytes)
        assertArrayEquals(byteArrayOf(0, 0), transport.reports[1].bytes)
        assertArrayEquals(byteArrayOf(0x41, 0), transport.reports[2].bytes)
        for (key in RemoteKey.entries) controller.sendKey(key)
        controller.powerOn(); controller.powerOff()
        assertEquals(52, transport.reports.size)
        controller.pause(); runCurrent()
    }

    @Test fun externalWakeRestartsPausedBudgetWithoutReplacingProfilePairingOrSendingKeys() = runTest {
        var opens = 0
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { opens++; transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); advanceTimeBy(101_001); runCurrent()
        assertEquals(3, transport.reconnects)
        controller.reconnectAfterWake(); controller.reconnectAfterWake(); runCurrent()
        assertEquals(4, transport.reconnects)
        assertEquals(1, opens)
        assertEquals(0, transport.pairingRequests)
        assertTrue(transport.reports.isEmpty())
        assertFalse(transport.closed)
        advanceTimeBy(101_001); runCurrent()
        assertEquals(7, transport.reconnects)
        advanceTimeBy(240_000); runCurrent(); assertEquals(7, transport.reconnects)
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        controller.reconnectAfterWake(); runCurrent()
        assertEquals(7, transport.reconnects)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
    }

    @Test fun externalWakeInterruptsBackoffWithoutWaitingForSetupOrUnregisteringHid() = runTest {
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory { transport }, backgroundScope, { host }, {})
        controller.retry(); runCurrent(); transport.channel.send(HidEvent.CONNECTED); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        controller.reconnectAfterWake(); runCurrent()
        assertEquals(1, transport.reconnects)
        assertFalse(transport.closed)
        assertEquals(0, transport.pairingRequests)
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        advanceTimeBy(3_000); runCurrent(); assertEquals(1, transport.reconnects)
    }

    @Test fun automaticRecoveryOfSavedUnbondedHostNeverRequestsPairing() = runTest {
        val transport = Transport(false)
        var opens = 0
        val controller = HidStreamerController(HidTransportFactory { opens++; transport }, backgroundScope, { host }, {})
        controller.reconnectAfterWake(); runCurrent()
        controller.reconnectAfterWake(); runCurrent(); advanceTimeBy(240_000); runCurrent()
        assertEquals(1, opens)
        assertEquals(0, transport.pairingRequests)
        assertEquals(0, transport.reconnects)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.pause(); runCurrent(); assertTrue(transport.closed)
    }

    @Test fun disconnectCancelsPendingWakeRetriesAndUnconfiguredWakeDoesNothing() = runTest {
        var saved: HidHost? = host
        val transport = Transport()
        var opens = 0
        val controller = HidStreamerController(HidTransportFactory { opens++; transport }, backgroundScope, { saved }, { saved = it })
        controller.reconnectAfterWake(); runCurrent(); advanceTimeBy(101_001); runCurrent()
        controller.reconnectAfterWake(); runCurrent()
        controller.pause(); runCurrent(); advanceTimeBy(240_000); runCurrent()
        assertTrue(transport.closed)
        assertEquals(4, transport.reconnects)
        controller.forget(); controller.reconnectAfterWake(); runCurrent()
        assertEquals(1, opens)
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
    }

    @Test fun wakeRecoveryUsesOnlyTheSelectedTransport() {
        val lan = com.myremote.app.data.FakeStreamerController()
        val bluetooth = com.myremote.app.data.FakeStreamerController()
        var mode = StreamerConnection.BLUETOOTH
        val route = StreamerRoute(lan, bluetooth) { mode }
        route.reconnectAfterWake()
        assertEquals(0, lan.wakeRecoveries); assertEquals(1, bluetooth.wakeRecoveries)
        mode = StreamerConnection.LAN
        route.reconnectAfterWake()
        assertEquals(1, lan.wakeRecoveries); assertEquals(1, bluetooth.wakeRecoveries)
    }

}
