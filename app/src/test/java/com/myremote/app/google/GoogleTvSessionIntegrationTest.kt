package com.myremote.app.google

import com.myremote.app.data.FakeSoundbarController
import com.myremote.app.data.FakeTvController
import com.myremote.app.domain.*
import com.myremote.app.google.protocol.*
import java.net.InetAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class GoogleTvSessionIntegrationTest {
    @Test fun mutualTlsNegotiationHeartbeatAllKeysMacroAndReportedPowerUseProductionSession() = runBlocking<Unit> {
        val pong = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val expected = listOf(19, 20, 21, 22, 23, 4, 3, 85, 89, 90, 166, 167) + (7..16)
        withTls(server = { socket, _, _ ->
            // Reference wire vectors: configure(KEY|PING|POWER), set-active, remote-start(false), ping(42).
            fragmented(socket, hex("0a020823"))
            assertEquals(35L, ProtoWire.fields(ProtoWire.fields(read(socket)).data(1)!!).number(1))
            fragmented(socket, hex("12020801"))
            assertEquals(35L, ProtoWire.fields(ProtoWire.fields(read(socket)).data(2)!!).number(1))
            fragmented(socket, hex("c202020800"))
            fragmented(socket, hex("4202082a"))
            assertArrayEquals(hex("4a02082a"), read(socket))
            pong.complete(Unit)
            val keys = mutableListOf<Pair<Int, Int>>()
            var longPong = false
            while (keys.size < expected.size + 5 || !longPong) {
                val payload = read(socket)
                val fields = ProtoWire.fields(payload)
                if (fields.data(9) != null) {
                    assertEquals(55L, ProtoWire.fields(fields.data(9)!!).number(1))
                    assertTrue(keys.contains(23 to RemoteProtocol.END_LONG))
                    longPong = true
                } else {
                    val key = ProtoWire.fields(fields.data(10)!!)
                    keys += key.number(1)!!.toInt() to key.number(2)!!.toInt()
                    if (keys.last() == (23 to RemoteProtocol.START_LONG)) fragmented(socket, hex("42020837"))
                }
            }
            assertEquals(expected.map { it to 3 } + listOf(23 to 1, 23 to 2, 23 to 3, 224 to 3, 223 to 3), keys)
            done.complete(Unit)
        }) { socket, _, _ ->
            val session = GoogleTvCommandSession(socket)
            assertTrue(runCatching { session.inject(23, false) }.exceptionOrNull() is DeviceFailure)
            val coordinator = RemoteCoordinator(FakeTvController(), object : StreamerController {
                override val connectionState = ConnectionState.CONNECTED
                override suspend fun sendKey(key: RemoteKey, pressKind: PressKind) = session.inject(KeyMapping.androidCode(key), pressKind == PressKind.LONG)
                override suspend fun powerOn() = session.inject(224, false)
                override suspend fun powerOff() = session.inject(223, false)
            }, FakeSoundbarController())
            val ready = CompletableDeferred<Unit>()
            val reader = launch(Dispatchers.IO) { session.run({ ready.complete(Unit) }, { coordinator.updateStreamerPower(it) }) }
            try {
                withTimeout(10_000) {
                    ready.await(); pong.await()
                    assertFalse(coordinator.state.streamerPowerOn) // Connected standby is still command-ready.
                    RemoteKey.entries.forEach { assertNull(coordinator.dispatch(RemoteAction.Key(it)).errorMessage) }
                    assertNull(coordinator.dispatch(RemoteAction.LastChannel).errorMessage)
                    coordinator.dispatch(RemoteAction.SelectInput(InputSource.XIAOMI))
                    assertNull(coordinator.dispatch(RemoteAction.Power).errorMessage) // WAKEUP, not SLEEP.
                    assertNull(coordinator.dispatch(RemoteAction.Power).errorMessage)
                    done.await(); reader.join()
                }
            } finally { reader.cancelAndJoin() }
        }
    }

    @Test fun pairingUsesCertificateBoundCodeOverRealMutualTls() = runBlocking<Unit> {
        withTls(server = { socket, client, server ->
            val request = ProtoWire.fields(read(socket))
            assertEquals(2L, request.number(1)); assertEquals(200L, request.number(2))
            assertEquals("atvremote", ProtoWire.fields(request.data(10)!!).data(1)!!.toString(Charsets.UTF_8))
            fragmented(socket, hex("080210c8015a00"))
            assertNotNull(ProtoWire.fields(read(socket)).data(20))
            fragmented(socket, hex("080210c801a20106120408031006"))
            assertNotNull(ProtoWire.fields(read(socket)).data(30))
            fragmented(socket, hex("080210c801fa0100"))
            val hash = independentSecret(client, server)
            val secret = ProtoWire.fields(ProtoWire.fields(read(socket)).data(40)!!).data(1)!!
            assertArrayEquals(hash, secret)
            fragmented(socket, hex("080210c801ca02220a20") + hash)
        }) { socket, client, server ->
            val handshake = PairingHandshake()
            ProtoWire.writeFrame(socket.outputStream, handshake.request("My Remote"))
            repeat(3) { handshake.accept(GoogleTvSocketIo.readFrame(socket)!!)?.let { ProtoWire.writeFrame(socket.outputStream, it) } }
            assertEquals(PairingHandshake.Step.CODE_REQUIRED, handshake.step)
            val hash = independentSecret(client, server)
            val code = "%02X1234".format(hash[0].toInt() and 255)
            val secret = PairingProtocol.secretHash(client.certificate.publicKey as RSAPublicKey, server.certificate.publicKey as RSAPublicKey, code)
            ProtoWire.writeFrame(socket.outputStream, handshake.submitSecret(secret))
            handshake.accept(GoogleTvSocketIo.readFrame(socket)!!)
            assertEquals(PairingHandshake.Step.COMPLETE, handshake.step)
        }
    }

    @Test fun remoteErrorAndOversizedFrameCannotProduceConnectedState() = runBlocking<Unit> {
        for (oversized in listOf(false, true)) {
            var ready = false
            withTls(server = { socket, _, _ ->
                if (!oversized) ProtoWire.writeFrame(socket.outputStream, hex("1a00"))
                else { socket.outputStream.write(ProtoWire.varint(ProtoWire.MAX_FRAME.toLong() + 1)); socket.outputStream.flush() }
            }) { socket, _, _ ->
                assertTrue(runCatching { GoogleTvCommandSession(socket).run({ ready = true }) }.isFailure)
                assertFalse(ready)
            }
        }
    }

    private suspend fun withTls(
        server: (SSLSocket, HeldCertificate, HeldCertificate) -> Unit,
        client: suspend CoroutineScope.(SSLSocket, HeldCertificate, HeldCertificate) -> Unit,
    ) = coroutineScope {
        val clientCertificate = HeldCertificate.Builder().rsa2048().commonName("MyRemote simulator client").build()
        val serverCertificate = HeldCertificate.Builder().rsa2048().commonName("Google TV simulator").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(serverCertificate).addTrustedCertificate(clientCertificate.certificate).build()
        val clientTls = HandshakeCertificates.Builder().heldCertificate(clientCertificate).build()
        val listener = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("::1")) as SSLServerSocket
        listener.needClientAuth = true
        listener.soTimeout = 10_000
        val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try { (listener.accept() as SSLSocket).use { socket ->
                socket.soTimeout = 10_000; socket.startHandshake()
                assertArrayEquals(clientCertificate.certificate.encoded, socket.session.peerCertificates.single().encoded)
                server(socket, clientCertificate, serverCertificate)
            } } catch (error: Throwable) { failure.set(error) }
        }.apply { start() }
        try {
            val context = SSLContext.getInstance("TLS").apply {
                init(arrayOf(clientTls.keyManager), arrayOf(GoogleTvTrustManager(AndroidClientIdentity.certificatePin(serverCertificate.certificate))), null)
            }
            val socket = GoogleTvSocketIo.work { own ->
                (context.socketFactory.createSocket() as SSLSocket).also {
                    own(it); it.soTimeout = 10_000; it.connect(java.net.InetSocketAddress("::1", listener.localPort), 3_000); it.startHandshake()
                }
            }
            socket.use { client(it, clientCertificate, serverCertificate) }
        } finally { listener.close(); thread.join(11_000) }
        assertFalse("Local server leaked", thread.isAlive)
        failure.get()?.let { throw AssertionError("Local Google TV server failed", it) }
    }
    private fun read(socket: SSLSocket) = requireNotNull(ProtoWire.readFrame(socket.inputStream))
    private fun fragmented(socket: SSLSocket, payload: ByteArray) {
        ProtoWire.frame(payload).forEach { socket.outputStream.write(it.toInt() and 255); socket.outputStream.flush() }
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun independentSecret(client: HeldCertificate, server: HeldCertificate): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(client, server).forEach {
            val key = it.certificate.publicKey as RSAPublicKey
            for (integer in listOf(key.modulus, key.publicExponent)) {
                val bytes = integer.toByteArray()
                digest.update(if (bytes.first() == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes)
            }
        }
        digest.update(byteArrayOf(0x12, 0x34))
        return digest.digest()
    }
}
