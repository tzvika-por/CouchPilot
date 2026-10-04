package com.myremote.app.lg

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LgAuthorizationTest {
    @Test fun deniedInputStopsReconnectAndRefreshRetainsPinButOmitsOldKey() = runTest {
        val fixture = fixture(LgProtocol.SWITCH_INPUT)
        val controller = fixture.controller
        controller.connectStored()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        try { controller.switchInput(InputSource.XIAOMI); fail("Expected permission denial") }
        catch (error: LgAuthorizationException) { assertEquals(401, error.errorCode) }
        runCurrent()
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, controller.connectionState)
        assertNull(controller.error.value)
        assertNull(fixture.store.read()!!.clientKey)
        assertTrue(fixture.store.read()!!.authorizationNeedsRefresh)
        assertTrue(fixture.transports.single().closed)
        controller.startDiscovery()
        controller.stopDiscovery()
        controller.retry()
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, fixture.transports.size)
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, controller.connectionState)

        controller.refreshAuthorization()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        val registration = fixture.transports.last().sent.map(::JSONObject).single { it.getString("type") == "register" }
        assertFalse(registration.getJSONObject("payload").has("client-key"))
        assertEquals(listOf("trusted-pin", "trusted-pin"), fixture.expectedPins)
        assertEquals("key-2", fixture.store.read()!!.clientKey)
        assertFalse(fixture.store.read()!!.authorizationNeedsRefresh)
        controller.switchInput(InputSource.XIAOMI)
        val command = fixture.transports.last().sent.map(::JSONObject).last()
        assertEquals("ssap://tv/switchInput", command.getString("uri"))
        assertEquals("HDMI_3", command.getJSONObject("payload").getString("inputId"))
        controller.close()
    }

    @Test fun legacyGrantWaitsForExplicitRefreshWithoutConnecting() = runTest {
        val fixture = fixture()
        fixture.preferences.edit().remove("authorization_revision").commit()
        fixture.controller.close()
        val controller = controller(fixture.store, fixture.factory)
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, controller.connectionState)
        controller.connectStored()
        controller.retry()
        runCurrent()
        assertTrue(fixture.transports.isEmpty())
        controller.refreshAuthorization()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, controller.connectionState)
        assertFalse(JSONObject(fixture.transports.single().sent.first { JSONObject(it).getString("type") == "register" })
            .getJSONObject("payload").has("client-key"))
        assertEquals(listOf("trusted-pin"), fixture.expectedPins)
        controller.close()
    }

    @Test fun refreshDuringRegistrationCancelsOldJobBeforeSavingNewGrant() = runTest {
        val fixture = fixture(pauseRegistration = true)
        fixture.controller.connectStored()
        runCurrent()
        assertEquals(ConnectionState.CONNECTING, fixture.controller.connectionState)
        fixture.controller.refreshAuthorization()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertTrue(fixture.transports.first().closed)
        assertEquals("key-2", fixture.store.read()!!.clientKey)
        assertFalse(JSONObject(fixture.transports.last().sent.first { JSONObject(it).getString("type") == "register" })
            .getJSONObject("payload").has("client-key"))
        assertEquals(listOf("trusted-pin", "trusted-pin"), fixture.expectedPins)
        fixture.controller.close()
    }

    @Test fun inputEnumeration401RequiresRefreshWithoutRetryLoop() = runTest {
        val fixture = fixture(LgProtocol.INPUT_LIST)
        fixture.controller.connectStored()
        runCurrent()
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, fixture.controller.connectionState)
        assertNull(fixture.store.read()!!.clientKey)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, fixture.transports.size)
        fixture.controller.close()
    }

    @Test fun registration401RemainsAuthorizationFailure() = runTest {
        val fixture = fixture("register")
        fixture.controller.connectStored()
        runCurrent()
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, fixture.controller.connectionState)
        assertTrue(fixture.store.read()!!.authorizationNeedsRefresh)
        assertEquals("trusted-pin", fixture.store.read()!!.certificatePin)
        fixture.controller.close()
    }

    @Test fun powerOff401AlsoRequiresRefresh() = runTest {
        val fixture = fixture(LgProtocol.TURN_OFF)
        fixture.controller.connectStored()
        runCurrent()
        try { fixture.controller.powerOff(); fail("Expected permission denial") }
        catch (_: LgAuthorizationException) { }
        runCurrent()
        assertEquals(ConnectionState.AUTHORIZATION_REQUIRED, fixture.controller.connectionState)
        assertTrue(fixture.store.read()!!.authorizationNeedsRefresh)
        fixture.controller.close()
    }

    private fun TestScope.controller(store: LgPairingStore, factory: LgTransportFactory) = LgTvController(
        store, factory, object : LgDiscovery {
            override val devices = MutableStateFlow<List<LgDevice>>(emptyList())
            override val error = MutableStateFlow<String?>(null)
            override fun start() = Unit
            override fun stop() = Unit
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), {},
    )

    private fun TestScope.fixture(deniedUri: String? = null, pauseRegistration: Boolean = false): Fixture {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        val store = LgPairingStore(prefs)
        store.select(LgDevice("LG", "192.0.2.8", wakeMacs = listOf("02:00:00:00:00:03")))
        store.registered("old-key", "trusted-pin")
        val transports = mutableListOf<ScriptedTransport>()
        val pins = mutableListOf<String?>()
        val factory = object : LgTransportFactory {
            override suspend fun connect(host: String, expectedPin: String?): LgTransport {
                assertEquals("192.0.2.8", host)
                pins += expectedPin
                return ScriptedTransport("key-${transports.size + 1}", deniedUri.takeIf { transports.isEmpty() },
                    pauseRegistration && transports.isEmpty())
                    .also { transports += it }
            }
        }
        return Fixture(controller(store, factory), store, prefs, factory, transports, pins)
    }

    private data class Fixture(
        val controller: LgTvController, val store: LgPairingStore,
        val preferences: LgPairingStoreTest.MemoryPreferences, val factory: LgTransportFactory,
        val transports: MutableList<ScriptedTransport>, val expectedPins: MutableList<String?>,
    )

    private class ScriptedTransport(
        private val key: String, private val deniedUri: String?, private val pauseRegistration: Boolean,
    ) : LgTransport {
        override val certificatePin = "trusted-pin"
        val sent = mutableListOf<String>()
        var closed = false
        private val incoming = Channel<String>(Channel.UNLIMITED)
        override suspend fun send(text: String) {
            sent += text
            val message = JSONObject(text)
            val id = message.getString("id")
            val type = message.getString("type")
            if (type == deniedUri || message.optString("uri") == deniedUri) {
                incoming.send("""{"type":"error","id":"$id","error":"401 insufficient permissions"}""")
            } else when (type) {
                "hello" -> incoming.send("""{"type":"hello"}""")
                "register" -> if (!pauseRegistration) incoming.send("""{"type":"registered","id":"$id","payload":{"client-key":"$key"}}""")
                "request" -> {
                    val payload = if (message.optString("uri") == LgProtocol.INPUT_LIST)
                        """{"returnValue":true,"devices":[{"id":"HDMI_2"},{"id":"HDMI_3"}]}"""
                    else """{"returnValue":true}"""
                    incoming.send("""{"type":"response","id":"$id","payload":$payload}""")
                }
            }
        }
        override suspend fun receive(): String? = incoming.receiveCatching().getOrNull()
        override fun close() { closed = true; incoming.close() }
    }
}
