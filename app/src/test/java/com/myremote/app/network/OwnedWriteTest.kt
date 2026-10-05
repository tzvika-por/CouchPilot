package com.myremote.app.network

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.google.GoogleTvSocketIo
import com.myremote.app.google.protocol.KeyInjector
import com.myremote.app.samsung.StreamSamsungTransport
import java.io.*
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OwnedWriteTest {
    private class BlockedOutput(private val passFirst: Int = 0) : OutputStream() {
        val entered = CountDownLatch(1); val exited = CountDownLatch(1); val closed = CountDownLatch(1)
        val calls = AtomicInteger()
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            if (calls.incrementAndGet() <= passFirst) return
            entered.countDown()
            try { check(closed.await(5, TimeUnit.SECONDS)) { "Test peer was never closed" }; throw IOException("Owned stream closed") }
            finally { exited.countDown() }
        }
        override fun close() { closed.countDown() }
    }
    private fun socket(out: OutputStream) = object : Socket() {
        override fun getOutputStream() = out
        override fun close() { out.close(); super.close() }
    }
    @Test fun normalGoogleFrameWriteSucceeds() = runBlocking {
        val out = ByteArrayOutputStream(); val socket = socket(out)
        GoogleTvSocketIo.writeFrame(socket, byteArrayOf(1, 2), 100)
        assertArrayEquals(byteArrayOf(2, 1, 2), out.toByteArray()); assertFalse(socket.isClosed)
    }
    @Test fun blockedGoogleFrameClosesOwnedSocketAndJoinsWorker() = runBlocking {
        val out = BlockedOutput(); val socket = socket(out)
        val error = withTimeout(2_000) { runCatching { GoogleTvSocketIo.writeFrame(socket, byteArrayOf(1), 100) }.exceptionOrNull() }
        assertTrue(error is DeviceFailure); assertTrue(socket.isClosed); assertEquals(0L, out.exited.count); assertEquals(1, out.calls.get())
    }
    @Test fun cancellationAlsoClosesAndTerminatesWorker() = runBlocking {
        val out = BlockedOutput(); val socket = socket(out)
        val task = launch { GoogleTvSocketIo.writeFrame(socket, byteArrayOf(1), 5_000) }
        withContext(Dispatchers.IO) { assertTrue(out.entered.await(1, TimeUnit.SECONDS)) }
        withTimeout(1_000) { task.cancelAndJoin() }; assertTrue(socket.isClosed); assertEquals(0L, out.exited.count)
    }
    @Test fun staleTimeoutCannotCloseNewReplacementSocket() = runBlocking {
        val old = socket(BlockedOutput()); val replacement = socket(ByteArrayOutputStream())
        withTimeout(2_000) { runCatching { GoogleTvSocketIo.writeFrame(old, byteArrayOf(1), 100) } }
        assertTrue(old.isClosed); assertFalse(replacement.isClosed)
        GoogleTvSocketIo.writeFrame(replacement, byteArrayOf(2)); assertFalse(replacement.isClosed)
    }
    @Test fun googleLongKeyReleaseCannotBlockForeverOrReplay() = runBlocking {
        // START_LONG is one framed write; END_LONG then blocks.
        val out = BlockedOutput(passFirst = 1); val socket = socket(out)
        val error = withTimeout(2_000) { runCatching {
            KeyInjector(send = { GoogleTvSocketIo.writeFrame(socket, it, 100) }, hold = {}).inject(23, true)
        }.exceptionOrNull() }
        assertTrue(error is DeviceFailure); assertTrue(socket.isClosed); assertEquals(0L, out.exited.count); assertEquals(2, out.calls.get())
    }
    @Test fun cancelledLongKeyStillBoundsItsNonCancellableRelease() = runBlocking {
        val out = BlockedOutput(passFirst = 1); val socket = socket(out)
        val holding = CompletableDeferred<Unit>()
        val key = launch { runCatching {
            KeyInjector(send = { GoogleTvSocketIo.writeFrame(socket, it, 100) },
                hold = { holding.complete(Unit); awaitCancellation() }).inject(23, true)
        } }
        holding.await()
        withTimeout(2_000) { key.cancelAndJoin() }
        assertTrue(socket.isClosed); assertEquals(0L, out.exited.count); assertEquals(2, out.calls.get())
    }
    @Test fun actualNonReadingTcpPeerIsAbortedAndNativeWorkerTerminates() = runBlocking {
        java.net.ServerSocket().use { server ->
            server.receiveBufferSize = 1024
            server.bind(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0))
            Socket().use { client ->
                client.sendBufferSize = 1024
                client.connect(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), server.localPort))
                server.accept().use {
                    val exited = CountDownLatch(1)
                    val error = withTimeout(2_000) { runCatching {
                        OwnedSocketWrite.run(100, { client.close() }) {
                            try { repeat(128) { client.outputStream.write(ByteArray(1024 * 1024)) }; client.outputStream.flush() }
                            finally { exited.countDown() }
                        }
                    }.exceptionOrNull() }
                    assertTrue(error is DeviceFailure); assertTrue(client.isClosed); assertEquals(0L, exited.count)
                }
            }
        }
    }

    @Test fun normalSamsungWriteSucceeds() = runBlocking {
        val out = ByteArrayOutputStream(); var closes = 0
        val transport = StreamSamsungTransport(ByteArrayInputStream(byteArrayOf()), out, { closes++ })
        transport.send(byteArrayOf(1, 2)); assertArrayEquals(byteArrayOf(1, 2), out.toByteArray()); assertEquals(0, closes)
    }
    @Test fun blockedSamsungWriteClosesOwnedTransportWithoutReplay() = runBlocking {
        val out = BlockedOutput(); var closes = 0
        val transport = StreamSamsungTransport(ByteArrayInputStream(byteArrayOf()), out, { closes++; out.close() }, 100)
        val error = withTimeout(2_000) { runCatching { transport.send(byteArrayOf(1)) }.exceptionOrNull() }
        assertTrue(error is DeviceFailure); assertEquals(1, closes); assertEquals(1, out.calls.get()); assertEquals(0L, out.exited.count)
    }
    @Test fun flushIsAlsoInsideTheEffectiveDeadline() = runBlocking {
        val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val out = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun flush() { entered.countDown(); released.await(5, TimeUnit.SECONDS); throw IOException("closed") }
            override fun close() { released.countDown() }
        }
        val s = socket(out)
        val error = withTimeout(2_000) { runCatching { GoogleTvSocketIo.writeFrame(s, byteArrayOf(1), 100) }.exceptionOrNull() }
        assertTrue(error is DeviceFailure); assertTrue(s.isClosed); assertEquals(0L, entered.count)
    }
}
