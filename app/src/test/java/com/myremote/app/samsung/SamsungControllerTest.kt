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

    @Test fun explicitPowerWhileDisconnectedAttemptsOneConnectionWithoutToggleReplay() = runTest {
        var suspended = true
        val transport = ScriptedSamsungTransport()
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++; transport
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        controller.connectStored(); runCurrent(); assertEquals(0, calls)
        val wake = async { controller.togglePower() }
        runCurrent(); wake.await()
        assertEquals(1, calls)
        assertFalse(suspended)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertEquals(0, transport.sent.count { it == "ff0b022001" })
        controller.volumeUp()
        assertFalse(transport.closed)
        controller.close()
    }

    @Test fun failedWakeRestoresSuppressionAndDoesNotKeepRetrying() = runTest {
        var calls = 0
        var suspended = true
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++; throw java.io.IOException("Standby service is unavailable")
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val wake = async {
            try { controller.togglePower(); fail("Wake must not be claimed") }
            catch (error: DeviceFailure) { assertEquals(FailureKind.WAKE_UNCONFIRMED, error.kind) }
        }
        runCurrent(); wake.await()
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertTrue(suspended)
        assertEquals(1, calls)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.close()
    }

    @Test fun silentWakeStatusClosesSocketWithoutSendingPowerToggle() = runTest {
        var suspended = true
        val transport = ScriptedSamsungTransport(autoReply = false)
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++; transport
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val wake = async {
            try { controller.togglePower(); fail("No status") }
            catch (error: DeviceFailure) { assertEquals(FailureKind.WAKE_UNCONFIRMED, error.kind) }
        }
        runCurrent(); advanceTimeBy(4_001); runCurrent(); wake.await()
        assertTrue(transport.closed)
        assertTrue(suspended)
        assertEquals(0, transport.sent.count { it == "ff0b022001" })
        advanceTimeBy(120_000); runCurrent(); assertEquals(1, calls)
        controller.close()
    }

    @Test fun wakeConnectionTimeoutCancelsSocketOwnerAndStopsLateReconnect() = runTest {
        var suspended = true
        var cancelled = false
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++
            try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val wake = async {
            try { controller.togglePower(); fail("No connection") }
            catch (error: DeviceFailure) { assertEquals(FailureKind.WAKE_UNCONFIRMED, error.kind) }
        }
        runCurrent(); advanceTimeBy(15_001); runCurrent(); wake.await()
        assertTrue(cancelled)
        assertTrue(suspended)
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        controller.close()
    }

    @Test fun deniedWakeKeepsPermissionFailureAndDoesNotTriggerBondOrRetry() = runTest {
        var suspended = true
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            calls++; throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Bluetooth denied")
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val wake = async {
            try { controller.togglePower(); fail("Permission") }
            catch (error: DeviceFailure) { assertEquals(FailureKind.PERMISSION_DENIED, error.kind) }
        }
        runCurrent(); wake.await()
        assertTrue(suspended)
        controller.connectStored(); advanceTimeBy(120_000); runCurrent(); assertEquals(1, calls)
        controller.close()
    }

    @Test fun soundIntentionRestoresControlAfterPowerOffAndProcessRecreationWithoutToggle() = runTest {
        var suspended = false
        val transports = mutableListOf<ScriptedSamsungTransport>()
        fun create() = SamsungSoundbarController(SamsungTransportFactory {
            ScriptedSamsungTransport().also { transports += it }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val first = create()
        first.connectStored(); runCurrent()
        first.togglePower(); runCurrent()
        assertTrue(suspended)
        first.close()
        val reopened = create()
        reopened.connectStored(); runCurrent()
        assertEquals(1, transports.size) // Opening the UI alone respects the Off intention.
        val volume = async { reopened.volumeDown() }
        runCurrent(); volume.await()
        assertFalse(suspended)
        assertEquals(ConnectionState.CONNECTED, reopened.connectionState)
        assertEquals(2, transports.size)
        assertEquals(1, transports.last().sent.count { it == "ff0b037f0100" })
        assertEquals(0, transports.last().sent.count { it == "ff0b022001" })
        reopened.mute()
        assertEquals(true, reopened.muted.value)
        assertEquals(2, transports.size) // Existing live connection is retained.
        reopened.close()
    }

    @Test fun disconnectedMuteRestoresControlBeforeExactlyOneMuteToggle() = runTest {
        var suspended = true
        val transport = ScriptedSamsungTransport()
        val controller = SamsungSoundbarController(SamsungTransportFactory { transport },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val mute = async { controller.mute() }
        runCurrent(); mute.await()
        assertEquals(true, controller.muted.value)
        assertFalse(suspended)
        assertEquals(1, transport.sent.count { it == "ff0b027400" })
        assertEquals(0, transport.sent.count { it == "ff0b022001" })
        controller.close()
    }

    @Test fun unavailableSoundCommandStopsWithoutReplayOrBackgroundRetries() = runTest {
        var suspended = true
        var attempts = 0
        val transport = ScriptedSamsungTransport(autoReply = false)
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            attempts++; transport
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val volume = async {
            try { controller.volumeUp(); fail("No status connection") }
            catch (error: DeviceFailure) { assertEquals(FailureKind.WAKE_UNCONFIRMED, error.kind) }
        }
        runCurrent(); advanceTimeBy(4_001); runCurrent(); volume.await()
        assertTrue(suspended)
        assertTrue(transport.closed)
        assertEquals(0, transport.sent.count { it == "ff0b037f0101" })
        assertEquals(0, transport.sent.count { it == "ff0b022001" })
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, attempts)
        controller.close()
    }

    @Test fun cancelledSoundReconnectRestoresSuppressionAndCancelsTransportOwner() = runTest {
        var suspended = true
        var cancelled = false
        var attempts = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            attempts++
            try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), { "bond" }, {},
            { suspended }, { suspended = it })
        val volume = async { controller.volumeUp() }
        runCurrent(); volume.cancel(); runCurrent()
        assertTrue(cancelled)
        assertTrue(suspended)
        controller.connectStored(); advanceTimeBy(120_000); runCurrent()
        assertEquals(1, attempts)
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
