package com.myremote.app.lg

import com.myremote.app.domain.InputSource
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LgProtocolTest {
    @Test fun registrationEncodesPromptPermissionsAndStoredKey() {
        val first = JSONObject(LgProtocol.register("reg_1", null))
        assertEquals("register", first.getString("type"))
        assertEquals("PROMPT", first.getJSONObject("payload").getString("pairingType"))
        assertFalse(first.getJSONObject("payload").has("client-key"))
        val permissions = first.getJSONObject("payload").getJSONObject("manifest").getJSONArray("permissions")
        assertTrue((0 until permissions.length()).map(permissions::getString).containsAll(
            listOf("READ_INPUT_DEVICE_LIST", "CONTROL_INPUT_TV", "CONTROL_POWER")))
        val later = JSONObject(LgProtocol.register("reg_2", "saved-key"))
        assertEquals("saved-key", later.getJSONObject("payload").getString("client-key"))
    }

    @Test fun requestsEncodeDecodeAndCorrelateOutOfOrderReplies() = runBlocking {
        val requests = LgRequests()
        val (firstId, first) = requests.open()
        val (secondId, second) = requests.open()
        assertTrue(firstId != secondId)
        val encoded = JSONObject(LgProtocol.request(firstId, LgProtocol.SWITCH_INPUT,
            JSONObject().put("inputId", "HDMI_3")))
        assertEquals("HDMI_3", encoded.getJSONObject("payload").getString("inputId"))
        assertFalse(requests.complete(LgProtocol.decode("""{"type":"response","id":"unknown"}""")))
        assertTrue(requests.complete(LgProtocol.decode("""{"type":"response","id":"$secondId","payload":{"returnValue":true}}""")))
        assertFalse(first.isCompleted)
        assertEquals(secondId, second.await().id)
        assertTrue(requests.complete(LgProtocol.decode("""{"type":"response","id":"$firstId","payload":{"returnValue":true}}""")))
        assertEquals(firstId, first.await().id)
    }

    @Test fun registrationWaitsForApprovalAndThenUsesCorrelatedCommands() = runBlocking {
        val transport = ScriptedTransport()
        val session = LgSsapSession(transport, this)
        var approvalPrompts = 0
        assertEquals("new-client-key", session.register(null) { approvalPrompts++ })
        assertEquals(1, approvalPrompts)
        assertEquals("pin", session.certificatePin)
        val inputs = LgProtocol.inputIds(session.request(LgProtocol.INPUT_LIST))
        assertEquals("HDMI_3", LgProtocol.matchingInput(InputSource.XIAOMI, inputs))
        session.request(LgProtocol.SWITCH_INPUT, JSONObject().put("inputId", "HDMI_3"))
        assertTrue(transport.sent.any { JSONObject(it).optString("uri") == LgProtocol.SWITCH_INPUT })
        session.close()
    }

    @Test fun inputMappingUsesStableIdsAndRejectsMissingInput() {
        val inputs = setOf("hdmi_1", "HDMI_2", "HDMI_3", "HDMI_4")
        assertEquals("hdmi_1", LgProtocol.matchingInput(InputSource.PS5, inputs))
        assertEquals("HDMI_2", LgProtocol.matchingInput(InputSource.MAC_MINI, inputs))
        assertEquals("HDMI_3", LgProtocol.matchingInput(InputSource.XIAOMI, inputs))
        assertEquals("HDMI_4", LgProtocol.matchingInput(InputSource.PC, inputs))
        assertThrows(IOException::class.java) { LgProtocol.matchingInput(InputSource.PC, setOf("HDMI_1")) }
    }

    @Test fun errorsAndFalseReturnValueAreNotSuccessful() {
        assertThrows(IOException::class.java) {
            LgProtocol.requireSuccess(LgProtocol.decode("""{"type":"error","id":"a","error":"401 denied"}"""))
        }
        assertThrows(IOException::class.java) {
            LgProtocol.requireSuccess(LgProtocol.decode("""{"type":"response","id":"a","payload":{"returnValue":false}}"""))
        }
    }

    @Test fun rejectedRegistrationDoesNotProduceClientKey() = runBlocking {
        val transport = object : LgTransport {
            override val certificatePin = "pin"
            private val messages = Channel<String>(Channel.UNLIMITED)
            override suspend fun send(text: String) {
                val type = JSONObject(text).getString("type")
                messages.send(if (type == "hello") """{"type":"hello","payload":{}}"""
                    else """{"type":"error","id":"myremote_register","error":"401 denied"}""")
            }
            override suspend fun receive(): String? = messages.receiveCatching().getOrNull()
            override fun close() { messages.close() }
        }
        val session = LgSsapSession(transport, this)
        try { session.register(null) {}; fail("Registration should have failed") }
        catch (_: LgRegistrationException) { /* expected */ }
        session.close()
    }

    @Test fun ssdpFiltersWebOsAndRetainsHostAndUuid() {
        val response = "HTTP/1.1 200 OK\r\nST: ${LgSsdp.service}\r\nUSN: uuid:lg-tv::${LgSsdp.service}\r\nLOCATION: http://192.0.2.8:3000/description.xml\r\n\r\n"
        val device = LgSsdp.candidate(response, InetAddress.getByName("192.0.2.8"))!!
        assertEquals("192.0.2.8", device.host)
        assertEquals("uuid:lg-tv", device.uuid)
        assertEquals(null, LgSsdp.candidate(response.replace(LgSsdp.service, "other-service"),
            InetAddress.getByName("192.0.2.8")))
    }

    @Test fun wakePacketAndReconnectDelaysAreDeterministic() {
        val configured = LgInstallation.forSelectedDevice(LgDevice("LG TV", "192.0.2.8"))
        assertEquals(listOf("02:00:00:00:00:03", "02:00:00:00:00:01"), configured.wakeMacs)
        val packet = LgWakeOnLan.packet("02:00:00:00:00:03")
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xff.toByte() }, packet.copyOfRange(0, 6))
        val address = byteArrayOf(0x78, 0x5d, 0xc8.toByte(), 0xbd.toByte(), 0xd6.toByte(), 0x2f)
        repeat(16) { index -> assertArrayEquals(address, packet.copyOfRange(6 + index * 6, 12 + index * 6)) }
        assertEquals(5_000L, LgReconnect.delayMillis(1))
        assertEquals(60_000L, LgReconnect.delayMillis(20))
    }

    private class ScriptedTransport : LgTransport {
        override val certificatePin = "pin"
        val sent = mutableListOf<String>()
        private val incoming = Channel<String>(Channel.UNLIMITED)

        override suspend fun send(text: String) {
            sent += text
            val message = JSONObject(text)
            when (message.getString("type")) {
                "hello" -> incoming.send("""{"type":"hello","payload":{"deviceUUID":"lg"}}""")
                "register" -> {
                    incoming.send("""{"type":"response","id":"myremote_register","payload":{"pairingType":"PROMPT"}}""")
                    incoming.send("""{"type":"registered","id":"myremote_register","payload":{"client-key":"new-client-key"}}""")
                }
                "request" -> {
                    val payload = if (message.getString("uri") == LgProtocol.INPUT_LIST)
                        """{"returnValue":true,"devices":[{"id":"HDMI_1"},{"id":"HDMI_3"}]}"""
                    else """{"returnValue":true}"""
                    incoming.send("""{"type":"response","id":"${message.getString("id")}","payload":$payload}""")
                }
            }
        }

        override suspend fun receive(): String? = incoming.receiveCatching().getOrNull()
        override fun close() { incoming.close() }
    }
}
