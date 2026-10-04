package com.myremote.app.samsung

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
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
        controller.volumeUp(); controller.mute()
        controller.disconnect(); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        assertTrue(transports.single().closed)
        controller.retry(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        controller.forget(); runCurrent(); assertNull(saved)
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
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
