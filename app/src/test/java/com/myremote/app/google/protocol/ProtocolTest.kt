package com.myremote.app.google.protocol

import com.myremote.app.domain.RemoteKey
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test fun frameReadsFragmentedInputAndRejectsTruncation() {
        val payload = ByteArray(300) { it.toByte() }
        val fragmenting = object : InputStream() {
            private val delegate = ByteArrayInputStream(ProtoWire.frame(payload))
            override fun read(): Int = delegate.read()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = delegate.read(bytes, offset, minOf(length, 1))
        }
        assertArrayEquals(payload, ProtoWire.readFrame(fragmenting))
        assertThrows(EOFException::class.java) { ProtoWire.readFrame(ByteArrayInputStream(byteArrayOf(4, 1))) }
    }

    @Test fun pairingTransitionsWaitForCodeAndSecretAck() {
        val handshake = PairingHandshake()
        handshake.request("My Remote")
        handshake.accept(PairingProtocol.wrap(PairingProtocol.REQUEST_ACK, byteArrayOf()))
        assertEquals(PairingHandshake.Step.OPTIONS_SENT, handshake.step)
        val tvOptions = ProtoWire.message(2, ProtoWire.concat(ProtoWire.integer(1, 3), ProtoWire.integer(2, 6)))
        handshake.accept(PairingProtocol.wrap(PairingProtocol.OPTIONS, tvOptions))
        handshake.accept(PairingProtocol.wrap(PairingProtocol.CONFIGURATION_ACK, byteArrayOf()))
        assertEquals(PairingHandshake.Step.CODE_REQUIRED, handshake.step)
        handshake.submitSecret(ByteArray(32))
        handshake.accept(PairingProtocol.wrap(PairingProtocol.SECRET_ACK, ProtoWire.bytes(1, ByteArray(32))))
        assertEquals(PairingHandshake.Step.COMPLETE, handshake.step)
    }

    @Test fun pairingSecretValidatesCodeAndUsesRsaKeys() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }
        val client = generator.generateKeyPair().public as RSAPublicKey
        val server = generator.generateKeyPair().public as RSAPublicKey
        val suffix = "00AB"
        val first = (0..255).first { candidate ->
            val code = "%02X%s".format(candidate, suffix)
            runCatching { PairingProtocol.secretHash(client, server, code) }.isSuccess
        }
        val hash = PairingProtocol.secretHash(client, server, "%02X%s".format(first, suffix))
        assertEquals(first, hash[0].toInt() and 0xff)
        assertEquals(32, hash.size)
        assertThrows(IllegalArgumentException::class.java) { PairingProtocol.secretHash(client, server, "invalid") }
    }

    @Test fun mapsNavigationNumbersAndChannels() {
        assertEquals(21, KeyMapping.androidCode(RemoteKey.LEFT))
        assertEquals(23, KeyMapping.androidCode(RemoteKey.CENTER))
        assertEquals(7, KeyMapping.androidCode(RemoteKey.DIGIT_0))
        assertEquals(16, KeyMapping.androidCode(RemoteKey.DIGIT_9))
        assertEquals(166, KeyMapping.androidCode(RemoteKey.CHANNEL_UP))
        assertEquals(167, KeyMapping.androidCode(RemoteKey.CHANNEL_DOWN))
        val fields = ProtoWire.fields(ProtoWire.fields(RemoteProtocol.key(23, RemoteProtocol.SHORT)).data(10)!!)
        assertEquals(23L, fields.number(1))
        assertEquals(3L, fields.number(2))
    }

    @Test fun longPressSendsStartHoldEndThenDomainShort() = runBlocking {
        val events = mutableListOf<String>()
        val injector = KeyInjector(
            send = { bytes ->
                val fields = ProtoWire.fields(ProtoWire.fields(bytes).data(10)!!)
                events += "${fields.number(1)}:${fields.number(2)}"
            },
            hold = { events += "hold:$it" },
        )
        injector.inject(23, true)
        injector.inject(23, false)
        assertEquals(listOf("23:1", "hold:650", "23:2", "23:3"), events)
    }

    @Test fun remoteHandshakeAndPingMessagesParse() {
        assertEquals(RemoteMessage.Configure(35), RemoteProtocol.parse(ProtoWire.message(1, ProtoWire.integer(1, 35))))
        assertEquals(RemoteMessage.Ping(42), RemoteProtocol.parse(ProtoWire.message(8, ProtoWire.integer(1, 42))))
        assertTrue(ProtoWire.fields(RemoteProtocol.pingResponse(42)).any { it.number == 9 })
        assertEquals(1000, ReconnectPolicy.delayMillis(1))
        assertEquals(30000, ReconnectPolicy.delayMillis(20))
    }

    @Test fun commandSessionBecomesReadyOnlyAfterStartAndAnswersPing() {
        val session = RemoteSessionHandshake()
        assertTrue(!session.ready)
        val configureReply = session.accept(RemoteMessage.Configure(35))!!
        assertEquals(1, ProtoWire.fields(configureReply).single().number)
        val activeReply = session.accept(RemoteMessage.SetActive(1))!!
        assertEquals(2, ProtoWire.fields(activeReply).single().number)
        assertTrue(!session.ready)
        val pingReply = session.accept(RemoteMessage.Ping(7))!!
        assertEquals(7L, ProtoWire.fields(ProtoWire.fields(pingReply).data(9)!!).number(1))
        session.accept(RemoteMessage.Start(false))
        assertTrue(session.ready)
    }
}
