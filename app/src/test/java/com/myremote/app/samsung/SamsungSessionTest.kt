package com.myremote.app.samsung

import com.myremote.app.domain.DeviceFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SamsungSessionTest {
    @Test fun setupAndControlsUseStatusQueriesWithoutChangingInput() = runTest {
        val transport = ScriptedSamsungTransport()
        val session = SamsungSession(transport, backgroundScope)
        session.initialize(); session.volumeUp(); session.volumeDown(); session.mute()
        assertEquals(listOf("ff08020101", "ff0b027f00", "ff0b037f0101", "ff0b027f00",
            "ff0b037f0100", "ff0b027f00", "ff0b027400", "ff0b03741000"), transport.sent)
        session.close(); assertTrue(transport.closed)
    }
    @Test fun powerUsesOneVerifiedToggleWithoutWaitingForAnOffAcknowledgement() = runTest {
        val transport = ScriptedSamsungTransport()
        val session = SamsungSession(transport, backgroundScope)
        session.initialize()
        session.togglePower()
        assertEquals(listOf("ff0b027f00", "ff0b022001"), transport.sent.takeLast(2))
        session.close()
    }

    @Test fun powerDoesNotSendToggleToAnUnresponsiveSession() = runTest {
        val transport = ScriptedSamsungTransport(autoReply = false)
        val session = SamsungSession(transport, backgroundScope)
        val result = async { runCatching { session.togglePower() } }
        runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertTrue(result.await().isFailure)
        assertEquals(listOf("ff0b027f00"), transport.sent)
        assertTrue(transport.closed)
    }

    @Test fun unrelatedRepliesCannotFinishPendingVolumeQuery() = runTest {
        val transport = ScriptedSamsungTransport(autoReply = false)
        val session = SamsungSession(transport, backgroundScope)
        val setup = async { session.initialize() }
        runCurrent()
        transport.incoming.send(SamsungProtocol.Frame(11, 116, byteArrayOf(0, 1)))
        runCurrent(); assertFalse(setup.isCompleted)
        transport.incoming.send(SamsungProtocol.Frame(11, 127, byteArrayOf(0, 12, 50)))
        setup.await(); session.close()
    }
    @Test fun statusTimeoutClosesStreamToPreventLateReplyReuse() = runTest {
        val transport = ScriptedSamsungTransport(autoReply = false)
        val session = SamsungSession(transport, backgroundScope)
        val result = async { runCatching { session.initialize() } }
        runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertTrue(result.await().exceptionOrNull() is DeviceFailure)
        assertTrue(transport.closed)
        session.close()
    }
    @Test fun queryTimeoutStartsBeforeBlockedQueryWrite() = runTest {
        var closed = false; var writes = 0
        val transport = object : SamsungTransport {
            override suspend fun send(bytes: ByteArray) { writes++; kotlinx.coroutines.awaitCancellation() }
            override suspend fun receive(): SamsungProtocol.Frame? = kotlinx.coroutines.awaitCancellation()
            override fun close() { closed = true }
        }
        val session = SamsungSession(transport, backgroundScope)
        val result = async { runCatching { session.togglePower() } }
        runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertTrue("Query budget must include its blocked write", result.isCompleted)
        assertTrue(result.await().exceptionOrNull() is DeviceFailure); assertTrue(closed); assertEquals(1, writes)
    }

    @Test fun operationBudgetIncludesVolumeWriteAndStatusQueryTogether() = runTest {
        val original = ScriptedSamsungTransport(); var volumeStarted = false
        val transport = object : SamsungTransport by original {
            override suspend fun send(bytes: ByteArray) {
                if (bytes.contentEquals(SamsungProtocol.volumeUp())) {
                    volumeStarted = true; kotlinx.coroutines.delay(3_000); original.send(bytes)
                } else if (volumeStarted && bytes.contentEquals(SamsungProtocol.volumeQuery())) {
                    kotlinx.coroutines.delay(800) // Query sent, no status response.
                } else original.send(bytes)
            }
        }
        val session = SamsungSession(transport, backgroundScope); session.initialize()
        val result = async { runCatching { session.volumeUp() } }
        runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertTrue(result.isCompleted); assertTrue(result.await().exceptionOrNull() is DeviceFailure)
        assertTrue(original.closed)
        assertEquals(1, original.sent.count { it == "ff0b037f0101" })
    }
    @Test fun cancelledStatusQueryRetiresConnectionInsteadOfReusingLateReply() = runTest {
        val transport = ScriptedSamsungTransport(autoReply = false)
        val session = SamsungSession(transport, backgroundScope)
        val pending = async { session.initialize() }; runCurrent()
        pending.cancel(); runCurrent(); pending.join()
        assertTrue(transport.closed); session.close()
    }

    @Test fun concurrentCommandsRemainSerializedWithoutRequestIds() = runTest {
        val transport = ScriptedSamsungTransport()
        val session = SamsungSession(transport, backgroundScope)
        session.initialize()
        val up = async { session.volumeUp() }
        val mute = async { session.mute() }
        up.await(); mute.await()
        assertEquals(listOf("ff0b037f0101", "ff0b027f00", "ff0b027400", "ff0b03741000"), transport.sent.drop(2))
        session.close()
    }
}

internal class ScriptedSamsungTransport(private val autoReply: Boolean = true) : SamsungTransport {
    val incoming = Channel<SamsungProtocol.Frame>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()
    var closed = false
    var muteStatus = 1
    override suspend fun send(bytes: ByteArray) {
        check(!closed)
        val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        sent += hex
        if (autoReply) when (hex) {
            "ff0b027f00" -> incoming.send(SamsungProtocol.Frame(11, 127, byteArrayOf(0, 12, 50)))
            "ff0b03741000" -> incoming.send(SamsungProtocol.Frame(11, 116, byteArrayOf(0, muteStatus.toByte())))
        }
    }
    override suspend fun receive(): SamsungProtocol.Frame? = incoming.receiveCatching().getOrNull()
    override fun close() { closed = true; incoming.close() }
}
