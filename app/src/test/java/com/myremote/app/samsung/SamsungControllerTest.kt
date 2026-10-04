package com.myremote.app.samsung

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SamsungControllerTest {
    @Test fun selectedBondPersistsAndConnectedRequiresValidProtocolResponse() = runTest {
        var saved: String? = null
        val transports = mutableListOf<ScriptedSamsungTransport>()
        val controller = SamsungSoundbarController(SamsungTransportFactory { address ->
            assertEquals("02:00:00:00:00:01", address)
            ScriptedSamsungTransport().also { transports += it }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { saved }, { saved = it })
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
        controller.select(SamsungDevice("HW-M360", "02:00:00:00:00:01")); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertEquals("02:00:00:00:00:01", saved)
        assertNull(controller.muted.value)
        controller.volumeUp(); controller.mute()
        assertEquals(true, controller.muted.value)
        // Follow device status, not an optimistic toggle based on the previous UI value.
        transports.single().muteStatus = 0
        controller.mute()
        assertEquals(false, controller.muted.value)
        controller.volumeDown()
        assertNull(controller.muted.value)
        controller.disconnect(); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertTrue(transports.single().closed)
        assertNull(controller.muted.value)
        controller.retry(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        controller.forget(); runCurrent(); assertNull(saved)
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
        controller.close()
    }
    @Test fun powerSuspendsAutomaticConnectionsAcrossForegroundAndControllerRecreation() = runTest {
        var suspended = false
        var calls = 0
        val transports = mutableListOf<ScriptedSamsungTransport>()
        fun create() = SamsungSoundbarController(SamsungTransportFactory {
            calls++; ScriptedSamsungTransport().also { transports += it }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val controller = create()
        controller.connectStored(); runCurrent()
        controller.togglePower(); runCurrent()
        assertTrue(suspended)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertTrue(transports.single().closed)
        assertEquals(1, transports.single().sent.count { it == "ff0b022001" })
        assertTrue(runCatching { controller.togglePower() }.isFailure)
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, calls)
        controller.close()
        val reopened = create()
        reopened.connectStored(); runCurrent()
        assertEquals(1, calls)
        reopened.retry(); runCurrent()
        assertFalse(suspended)
        assertEquals(2, calls)
        assertEquals(ConnectionState.CONNECTED, reopened.connectionState)
        assertEquals(0, transports.last().sent.count { it == "ff0b022001" })
        reopened.close()
    }

    @Test fun standbyDisconnectDuringPendingPowerWriteCannotStartReconnect() = runTest {
        var calls = 0
        var writes = 0
        val completeWrite = kotlinx.coroutines.CompletableDeferred<Unit>()
        val scripted = ScriptedSamsungTransport()
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++
            object : SamsungTransport by scripted {
                override suspend fun send(bytes: ByteArray) {
                    if (bytes.contentEquals(SamsungProtocol.powerToggle())) {
                        writes++
                        scripted.incoming.close() // Device enters standby before write completes.
                        completeWrite.await()
                    } else scripted.send(bytes)
                }
            }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {})
        controller.retry(); runCurrent()
        val power = async { controller.togglePower() }
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(1, writes)
        completeWrite.complete(Unit); power.await()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.close()
    }

    @Test fun uncertainPowerWriteNeverReplaysOrReconnects() = runTest {
        var calls = 0
        var writes = 0
        var suspended = false
        val scripted = ScriptedSamsungTransport()
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++
            object : SamsungTransport by scripted {
                override suspend fun send(bytes: ByteArray) {
                    if (bytes.contentEquals(SamsungProtocol.powerToggle())) {
                        writes++
                        throw java.io.IOException("Write may have reached standby device")
                    }
                    scripted.send(bytes)
                }
            }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        controller.connectStored(); runCurrent()
        assertTrue(runCatching { controller.togglePower() }.isFailure)
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertTrue(suspended)
        assertTrue(scripted.closed)
        assertEquals(1, calls)
        assertEquals(1, writes)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.close()
    }

    @Test fun permissionDenialDoesNotReconnectAggressively() = runTest {
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++; throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Permission denied")
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {})
        controller.retry(); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(FailureKind.PERMISSION_DENIED, controller.error.value)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.close()
    }
    @Test fun transientFailureBacksOffAndNextSessionInitializes() = runTest {
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            if (++calls == 1) throw java.io.IOException("Connection lost")
            ScriptedSamsungTransport()
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {})
        controller.retry(); runCurrent()
        advanceTimeBy(2_999); runCurrent(); assertEquals(1, calls)
        advanceTimeBy(1); runCurrent(); assertEquals(2, calls)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        controller.close()
    }
    @Test fun successfulRfcommWithoutStatusNeverReportsConnected() = runTest {
        val transport = ScriptedSamsungTransport(autoReply = false)
        val controller = SamsungSoundbarController(SamsungTransportFactory { transport },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {})
        controller.retry(); runCurrent()
        assertEquals(ConnectionState.CONNECTING, controller.connectionState)
        advanceTimeBy(4_001); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertTrue(transport.closed)
        controller.close()
    }
}
