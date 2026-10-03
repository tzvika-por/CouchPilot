package com.myremote.app.lg

import android.annotation.SuppressLint
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal interface LgTransport : AutoCloseable {
    val certificatePin: String
    suspend fun send(text: String)
    suspend fun receive(): String?
}

internal interface LgTransportFactory {
    suspend fun connect(host: String, expectedPin: String?): LgTransport
}

/** A TV-specific WSS client. First approval pins the self-signed certificate; later connections require that pin. */
internal class OkHttpLgTransportFactory : LgTransportFactory {
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    override suspend fun connect(host: String, expectedPin: String?): LgTransport {
        val trust = LgTvTrustManager(expectedPin)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val client = OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true } // LG uses a self-signed LAN certificate; the pin is checked above.
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val endpoint = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val opened = CompletableDeferred<Unit>()
        val incoming = Channel<String>(Channel.UNLIMITED)
        val socket = client.newWebSocket(Request.Builder().url("wss://$endpoint:3001/").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
                override fun onMessage(webSocket: WebSocket, text: String) { incoming.trySend(text) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    opened.completeExceptionally(IOException("LG WebSocket closed during connection"))
                    incoming.close()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    opened.completeExceptionally(t)
                    incoming.close(t)
                }
            })
        try {
            withTimeout(10_000) { opened.await() }
            val pin = trust.seenPin ?: throw IOException("LG TLS certificate was not received")
            return object : LgTransport {
                override val certificatePin = pin
                override suspend fun send(text: String) {
                    if (!socket.send(text)) throw IOException("LG WebSocket is closed")
                }
                override suspend fun receive(): String? = incoming.receiveCatching().getOrThrow()
                override fun close() {
                    socket.close(1000, null)
                    socket.cancel()
                    incoming.close()
                    client.dispatcher.executorService.shutdown()
                    client.connectionPool.evictAll()
                }
            }
        } catch (error: Throwable) {
            socket.cancel()
            incoming.close()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            throw error
        }
    }
}

@SuppressLint("CustomX509TrustManager")
internal class LgTvTrustManager(private val expectedPin: String?) : X509TrustManager {
    @Volatile var seenPin: String? = null
        private set

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val certificate = chain?.firstOrNull() ?: throw CertificateException("LG sent no certificate")
        certificate.checkValidity()
        val pin = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (expectedPin != null && pin != expectedPin) throw CertificateException("LG certificate changed; pair again")
        seenPin = pin
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
