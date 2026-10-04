package com.myremote.app.lg

import java.security.MessageDigest
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LgTlsIntegrationTest {
    @Test fun productionWssTransportRegistersAndCorrelatesRealServerReplies() = runBlocking<Unit> {
        val certificate = HeldCertificate.Builder().commonName("Local TV simulator").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        MockWebServer().use { server ->
            server.useHttps(tls.sslSocketFactory(), false)
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val request = JSONObject(text)
                    val id = request.getString("id")
                    val response = when (request.getString("type")) {
                        "hello" -> JSONObject().put("type", "hello").put("payload", JSONObject().put("deviceUUID", "00000000-0000-4000-8000-000000000001"))
                        "register" -> {
                            assertEquals("saved-key", request.getJSONObject("payload").getString("client-key"))
                            JSONObject().put("type", "registered").put("payload", JSONObject().put("client-key", "saved-key"))
                        }
                        else -> JSONObject().put("type", "response").put("payload", JSONObject().put("returnValue", true)
                            .put("devices", org.json.JSONArray().put(JSONObject().put("id", "HDMI_3").put("appId", "tv.reported.hdmi"))))
                    }
                    webSocket.send(response.put("id", id).toString())
                }
            }))
            server.start()
            val session = LgSsapSession(OkHttpLgTransportFactory(port = server.port).connect("localhost", pin(certificate)), this)
            try {
                withTimeout(5_000) {
                    assertEquals("saved-key", session.register("saved-key") { fail("Stored grant must not prompt") })
                    assertEquals("00000000-0000-4000-8000-000000000001", session.deviceUuid)
                    assertEquals("tv.reported.hdmi", LgProtocol.inputs(session.request(LgProtocol.INPUT_LIST)).single().appId)
                    session.request(LgProtocol.SWITCH_INPUT, JSONObject().put("inputId", "HDMI_3"))
                }
            } finally { session.close() }
        }
    }
    @Test fun changedCertificateFailsRealTlsWithoutPlaintextFallback() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("Different TV").build()
        MockWebServer().use { server ->
            server.useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(), false)
            server.start()
            try {
                OkHttpLgTransportFactory(port = server.port).connect("localhost", "00".repeat(32))
                fail("Changed certificate must be rejected")
            } catch (error: SSLHandshakeException) { assertTrue(error.toString(), generateSequence<Throwable>(error) { it.cause }
                .any { it.message.orEmpty().contains("LG certificate changed") }) }
        }
    }
    private fun pin(certificate: HeldCertificate): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate.certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
}
