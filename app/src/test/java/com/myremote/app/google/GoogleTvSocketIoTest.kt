package com.myremote.app.google

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GoogleTvSocketIoTest {
    @Test fun cancellingBlockedFrameReadClosesNativeSocket() = runBlocking<Unit> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                server.accept().use { peer ->
                    val started = CompletableDeferred<Unit>()
                    val read = launch(Dispatchers.IO) {
                        GoogleTvSocketIo.work { own ->
                            own(client)
                            started.complete(Unit)
                            com.myremote.app.google.protocol.ProtoWire.readFrame(client.inputStream)
                        }
                        fail("Cancelled read returned normally")
                    }
                    started.await()
                    withTimeout(2_000) { read.cancelAndJoin() }
                    assertTrue(client.isClosed)
                    peer.soTimeout = 2_000
                    assertEquals(-1, peer.inputStream.read())
                }
            }
        }
    }
    @Test fun cancellingStalledTlsHandshakeClosesTheInFlightConnection() = runBlocking<Unit> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val started = CompletableDeferred<Unit>()
            val owned = AtomicReference<SSLSocket>()
            val connect = launch(Dispatchers.IO) {
                GoogleTvSocketIo.work { own ->
                    val socket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
                    own(socket); owned.set(socket)
                    socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 2_000)
                    started.complete(Unit)
                    socket.startHandshake() // Peer intentionally never speaks TLS.
                    socket
                }
                fail("Cancelled negotiation returned normally")
            }
            started.await()
            server.accept().use {
                withTimeout(2_000) { connect.cancelAndJoin() }
                assertTrue(owned.get().isClosed)
            }
        }
    }
}
