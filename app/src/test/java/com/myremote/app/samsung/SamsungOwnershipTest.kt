package com.myremote.app.samsung

import com.myremote.app.domain.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SamsungOwnershipTest {
    @Test fun cancelledOldAttemptCompletesAfterNewConnectedWithoutReplacingIt() = runTest {
        val gate = CompletableDeferred<Unit>(); val old = ScriptedSamsungTransport(); val fresh = ScriptedSamsungTransport()
        var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            if (++calls == 1) withContext(NonCancellable) { gate.await(); old } else fresh
        }, backgroundScope, { "synthetic-bond" }, {})
        controller.retry(); runCurrent(); controller.retry(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        gate.complete(Unit); runCurrent()
        assertTrue(old.closed); assertFalse(fresh.closed)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertNull(controller.error.value); controller.disconnect()
    }

    @Test fun lateOldFailureCannotOverwriteConnectedOrMutedState() = runTest {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            if (++calls == 1) withContext(NonCancellable) { gate.await(); throw java.io.IOException("old attempt") }
            else ScriptedSamsungTransport()
        }, backgroundScope, { "synthetic-bond" }, {})
        controller.retry(); runCurrent(); controller.retry(); runCurrent(); controller.mute()
        gate.complete(Unit); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertEquals(true, controller.muted.value); assertNull(controller.error.value); controller.disconnect()
    }

    @Test fun staleDisconnectAfterOverlappingReconnectDoesNotCloseFreshTransport() = runTest {
        val transports = mutableListOf<ScriptedSamsungTransport>()
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            ScriptedSamsungTransport().also { transports += it }
        }, backgroundScope, { "synthetic-bond" }, {})
        controller.retry(); runCurrent(); val old = transports.single()
        controller.retry(); runCurrent(); old.incoming.close(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertFalse(transports.last().closed); controller.disconnect()
    }

    @Test fun oldCommandFailureAfterNewConnectedCannotPublishDisconnect() = runTest {
        val gate = CompletableDeferred<Unit>(); var calls = 0
        val original = ScriptedSamsungTransport()
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            if (++calls == 1) object : SamsungTransport by original {
                override suspend fun send(bytes: ByteArray) {
                    if (bytes.contentEquals(SamsungProtocol.volumeUp())) withContext(NonCancellable) {
                        gate.await(); throw java.io.IOException("old command")
                    } else original.send(bytes)
                }
            } else ScriptedSamsungTransport()
        }, backgroundScope, { "synthetic-bond" }, {})
        controller.retry(); runCurrent()
        val command = async { runCatching { controller.volumeUp() } }; runCurrent()
        controller.retry(); runCurrent(); controller.mute()
        gate.complete(Unit); runCurrent(); assertTrue(command.await().isFailure)
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertEquals(true, controller.muted.value); assertNull(controller.error.value); controller.disconnect()
    }

    @Test fun writeTimeoutDisconnectsThenReconnectsWithoutReplayingVolume() = runTest {
        val original = ScriptedSamsungTransport(); val fresh = ScriptedSamsungTransport(); var connects = 0
        var physicalWrites = 0
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            if (++connects == 1) object : SamsungTransport by original {
                override suspend fun send(bytes: ByteArray) {
                    if (bytes.contentEquals(SamsungProtocol.volumeUp())) {
                        physicalWrites++; awaitCancellation()
                    } else original.send(bytes)
                }
            } else fresh
        }, backgroundScope, { "synthetic-bond" }, {})
        controller.retry(); runCurrent(); assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        val command = async { runCatching { controller.volumeUp() } }
        runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertTrue(command.isCompleted); assertTrue(command.await().isFailure); assertTrue(original.closed)
        assertEquals(ConnectionState.DISCONNECTED, controller.connectionState)
        advanceTimeBy(3_001); runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState); assertFalse(fresh.closed)
        assertEquals(2, connects); assertEquals(1, physicalWrites)
        assertFalse(fresh.sent.contains("ff0b037f0101")); controller.disconnect()
    }

    @Test fun disconnectInvalidatesLateConnectionAndForgetRemainsAuthoritative() = runTest {
        val gate = CompletableDeferred<Unit>(); val transport = ScriptedSamsungTransport(); var saved: String? = "bond"
        val controller = SamsungSoundbarController(SamsungTransportFactory {
            withContext(NonCancellable) { gate.await(); transport }
        }, backgroundScope, { saved }, { saved = it })
        controller.retry(); runCurrent(); controller.forget(); gate.complete(Unit); runCurrent()
        assertEquals(ConnectionState.NOT_CONFIGURED, controller.connectionState)
        assertNull(saved); assertTrue(transport.closed); assertNull(controller.error.value)
    }
}
