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
        assertEquals(ConnectionState.PAIRING, controller.connectionState)
        advanceTimeBy(120_001); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertEquals(FailureKind.NETWORK, controller.error.value)
        assertTrue(transport.closed)
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, count)
    }
    @Test fun bondedReconnectBacksOffAndPermissionFailureStops() = runTest {
        var attempts = 0
        val transport = Transport()
        val controller = HidStreamerController(HidTransportFactory {
            if (++attempts == 1) transport else throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Revoked")
        }, backgroundScope, { host }, {})
        controller.retry(); runCurrent()
        transport.channel.send(HidEvent.CONNECTED); runCurrent()
        transport.channel.send(HidEvent.DISCONNECTED); runCurrent()
        assertTrue(transport.closed)
        advanceTimeBy(2_999); runCurrent(); assertEquals(1, attempts)
        advanceTimeBy(1); runCurrent(); assertEquals(2, attempts)
        advanceTimeBy(120_000); runCurrent(); assertEquals(2, attempts)
        assertEquals(FailureKind.PERMISSION_DENIED, controller.error.value)
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
}
