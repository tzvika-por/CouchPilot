package com.myremote.app.samsung

import com.myremote.app.network.OwnedSocketWrite
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Immutable connection ownership: timeout closes this stream, never a newer replacement. */
internal class StreamSamsungTransport(
    private val input: InputStream,
    private val output: OutputStream,
    private val closeConnection: () -> Unit,
    private val writeTimeoutMillis: Long = 4_000,
) : SamsungTransport {
    override suspend fun send(bytes: ByteArray) {
        OwnedSocketWrite.run(writeTimeoutMillis, closeConnection) { output.write(bytes); output.flush() }
    }
    override suspend fun receive(): SamsungProtocol.Frame? = withContext(Dispatchers.IO) { SamsungProtocol.read(input) }
    override fun close() = closeConnection()
}
