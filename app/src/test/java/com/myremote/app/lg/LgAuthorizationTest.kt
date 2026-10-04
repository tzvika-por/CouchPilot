package com.myremote.app.lg

import com.myremote.app.domain.ConnectionState
import com.myremote.app.domain.InputSource
import kotlinx.coroutines.async
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
    @Test fun discoveryIdentityConflictCannotReconnectWithoutExistingPinOrRequestNewGrant() = runTest {
        val fixture = fixture()
        fixture.store.learnedIdentity("00000000-0000-4000-8000-000000000001", "trusted-pin")
        fixture.controller.select(LgDevice("Impostor", "192.0.2.8", uuid = "different-uuid"))
        runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(ConnectionState.ERROR, fixture.controller.connectionState)
        assertEquals("old-key", fixture.store.read()!!.clientKey)
        assertEquals("trusted-pin", fixture.store.read()!!.certificatePin)
        assertTrue(fixture.transports.isEmpty())
        fixture.controller.close()
    }

    @Test fun deniedInputRetainsWorkingGrantAndPowerControl() = runTest {
        val fixture = fixture(LgProtocol.SWITCH_INPUT)
        fixture.controller.connectStored()
        runCurrent()
        try { fixture.controller.switchInput(InputSource.XIAOMI); fail("Expected permission denial") }
        catch (error: LgAuthorizationException) { assertEquals(401, error.errorCode) }
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertEquals("key-1", fixture.store.read()!!.clientKey)
        assertFalse(fixture.store.read()!!.authorizationNeedsRefresh)
        assertFalse(fixture.transports.single().closed)
        fixture.controller.powerOff()
        assertEquals(ConnectionState.DISCONNECTED, fixture.controller.connectionState)
        fixture.controller.close()
    }

    @Test fun deniedSwitchUsesOnlyReportedInputAppIdWithoutPairingAgain() = runTest {
        val fixture = fixture(LgProtocol.SWITCH_INPUT, inputAppId = "reported.hdmi.app")
        fixture.controller.connectStored(); runCurrent()
        fixture.controller.switchInput(InputSource.XIAOMI)
        val request = JSONObject(fixture.transports.single().sent.last())
        assertEquals(LgProtocol.LAUNCH_INPUT, request.getString("uri"))
        assertEquals("reported.hdmi.app", request.getJSONObject("payload").getString("id"))
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertEquals(1, fixture.transports.size)
        fixture.controller.close()
    }

    @Test fun legacyGrantIsReusedWithoutForcingApprovalForManifestRevision() = runTest {
        val fixture = fixture()
        fixture.preferences.edit().remove("authorization_revision").commit()
        fixture.controller.connectStored(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        val registration = JSONObject(fixture.transports.single().sent.first { JSONObject(it).getString("type") == "register" })
        assertEquals("old-key", registration.getJSONObject("payload").getString("client-key"))
        fixture.controller.close()
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

    @Test fun inputEnumeration401RetainsRegistrationWithoutRetryLoop() = runTest {
        val fixture = fixture(LgProtocol.INPUT_LIST)
        fixture.controller.connectStored()
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertEquals("key-1", fixture.store.read()!!.clientKey)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, fixture.transports.size)
        fixture.controller.close()
    }

    @Test fun refreshedGrantIsReusedAcrossBackgroundingAndControllerRecreation() = runTest {
        val fixture = fixture()
        fixture.controller.refreshAuthorization(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        fixture.controller.pause(); runCurrent()
        fixture.controller.connectStored(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        fixture.controller.close(); runCurrent()
        val recreated = controller(LgPairingStore(fixture.preferences, testLgCipher()), fixture.factory)
        recreated.connectStored(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, recreated.connectionState)
        val registrations = fixture.transports.map { transport ->
            JSONObject(transport.sent.first { JSONObject(it).getString("type") == "register" }).getJSONObject("payload")
        }
        assertFalse(registrations[0].has("client-key")) // Explicit refresh only.
        assertEquals("key-1", registrations[1].getString("client-key"))
        assertEquals("key-2", registrations[2].getString("client-key"))
        assertTrue(registrations.all { !it.getBoolean("forcePairing") })
        assertFalse(fixture.store.read()!!.authorizationNeedsRefresh)
        recreated.close()
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

    @Test fun powerOff401RetainsRegistration() = runTest {
        val fixture = fixture(LgProtocol.TURN_OFF)
        fixture.controller.connectStored()
        runCurrent()
        try { fixture.controller.powerOff(); fail("Expected permission denial") }
        catch (_: LgAuthorizationException) { }
        runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertFalse(fixture.store.read()!!.authorizationNeedsRefresh)
        fixture.controller.close()
    }

    private val targetUuid = "00000000-0000-4000-8000-000000000001"

    @Test fun secureHelloCompletesLegacyManualWakeSetupWithoutApproval() = runTest {
        val fixture = fixture(helloUuid = targetUuid)
        fixture.preferences.edit().remove("wake_macs").remove("uuid").commit()
        fixture.controller.connectStored(); runCurrent()
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertEquals(targetUuid, fixture.store.read()!!.device.uuid)
        assertEquals(2, fixture.store.read()!!.device.wakeMacs.size)
        assertEquals("trusted-pin", fixture.store.read()!!.certificatePin)
        val registration = JSONObject(fixture.transports.single().sent.first { JSONObject(it).getString("type") == "register" })
        assertEquals("old-key", registration.getJSONObject("payload").getString("client-key"))
        assertFalse(registration.getJSONObject("payload").getBoolean("forcePairing"))
        fixture.controller.close()
    }

    @Test fun conflictingSecureHelloRejectsBeforeSendingStoredClientKey() = runTest {
        val fixture = fixture(helloUuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        fixture.preferences.edit().putString("uuid", targetUuid).commit()
        fixture.controller.connectStored(); runCurrent(); advanceTimeBy(120_000); runCurrent()
        assertEquals(ConnectionState.ERROR, fixture.controller.connectionState)
        assertEquals(1, fixture.transports.size)
        assertTrue(fixture.transports.single().closed)
        assertFalse(fixture.transports.single().sent.any { JSONObject(it).getString("type") == "register" })
        assertEquals("old-key", fixture.store.read()!!.clientKey)
        assertEquals("trusted-pin", fixture.store.read()!!.certificatePin)
        fixture.controller.close()
    }

    @Test fun reselectingSameHostSavesWakeMetadataAndReusesKey() = runTest {
        val fixture = fixture()
        fixture.preferences.edit().remove("wake_macs").remove("uuid").commit()
        fixture.controller.select(LgDevice("Living room", "192.0.2.8", uuid = "uuid:$targetUuid")); runCurrent()
        assertEquals(2, fixture.store.read()!!.device.wakeMacs.size)
        val registration = JSONObject(fixture.transports.single().sent.first { JSONObject(it).getString("type") == "register" })
        assertEquals("old-key", registration.getJSONObject("payload").getString("client-key"))
        fixture.controller.close()
    }

    @Test fun wakeUsesRecoveredManualConfigurationAndRequiresRegisteredConnection() = runTest {
        val wakes = mutableListOf<LgDevice>()
        val fixture = fixture(helloUuid = targetUuid, wakeAction = { wakes += it })
        fixture.preferences.edit().remove("wake_macs").commit()
        fixture.controller.connectStored(); runCurrent()
        fixture.controller.powerOff(); runCurrent()
        val power = async { fixture.controller.powerOn() }
        runCurrent(); advanceTimeBy(251); runCurrent(); power.await()
        assertEquals(2, wakes.single().wakeMacs.size)
        assertEquals(ConnectionState.CONNECTED, fixture.controller.connectionState)
        assertEquals(2, fixture.transports.size)
        assertEquals(listOf("trusted-pin", "trusted-pin"), fixture.expectedPins)
        fixture.controller.close()
    }

    @Test fun missingWakeAddressDoesNotStartNetworkingOrClaimConnecting() = runTest {
        var wakes = 0
        val fixture = fixture(wakeAction = { wakes++ })
        fixture.preferences.edit().remove("wake_macs").commit()
        try { fixture.controller.powerOn(); fail("Missing wake configuration") }
        catch (error: com.myremote.app.domain.DeviceFailure) {
            assertEquals(com.myremote.app.domain.FailureKind.WAKE_NOT_CONFIGURED, error.kind)
        }
        assertEquals(0, wakes)
        assertTrue(fixture.transports.isEmpty())
        assertEquals(ConnectionState.DISCONNECTED, fixture.controller.connectionState)
        assertEquals("old-key", fixture.store.read()!!.clientKey)
        fixture.controller.close()
    }

    @Test fun wakeTimeoutCancelsRegistrationAndPreservesCredentialsWithoutLateRetry() = runTest {
        val fixture = fixture(pauseRegistration = true)
        val power = async {
            try { fixture.controller.powerOn(); fail("No registered connection") }
            catch (error: com.myremote.app.domain.DeviceFailure) {
                assertEquals(com.myremote.app.domain.FailureKind.WAKE_UNCONFIRMED, error.kind)
            }
        }
        runCurrent(); advanceTimeBy(45_001); runCurrent(); power.await()
        assertTrue(fixture.transports.single().closed)
        assertEquals(ConnectionState.DISCONNECTED, fixture.controller.connectionState)
        assertEquals("old-key", fixture.store.read()!!.clientKey)
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, fixture.transports.size)
        fixture.controller.close()
    }

    private fun TestScope.controller(store: LgPairingStore, factory: LgTransportFactory, wakeAction: suspend (LgDevice) -> Unit = {}) = LgTvController(
        store, factory, object : LgDiscovery {
            override val devices = MutableStateFlow<List<LgDevice>>(emptyList())
            override val error = MutableStateFlow<String?>(null)
            override fun start() = Unit
            override fun stop() = Unit
        }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)), wakeAction,
    )

    private fun TestScope.fixture(deniedUri: String? = null, pauseRegistration: Boolean = false, inputAppId: String? = null, helloUuid: String? = null, wakeAction: suspend (LgDevice) -> Unit = {}): Fixture {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        val store = LgPairingStore(prefs, testLgCipher())
        store.select(LgDevice("LG", "192.0.2.8", wakeMacs = listOf("02:00:00:00:00:03")))
        store.registered("old-key", "trusted-pin")
        val transports = mutableListOf<ScriptedTransport>()
        val pins = mutableListOf<String?>()
        val factory = object : LgTransportFactory {
            override suspend fun connect(host: String, expectedPin: String?): LgTransport {
                assertEquals("192.0.2.8", host)
                pins += expectedPin
                return ScriptedTransport("key-${transports.size + 1}", deniedUri.takeIf { transports.isEmpty() },
                    pauseRegistration && transports.isEmpty(), inputAppId, helloUuid)
                    .also { transports += it }
            }
        }
        return Fixture(controller(store, factory, wakeAction), store, prefs, factory, transports, pins)
    }

    private data class Fixture(
        val controller: LgTvController, val store: LgPairingStore,
        val preferences: LgPairingStoreTest.MemoryPreferences, val factory: LgTransportFactory,
        val transports: MutableList<ScriptedTransport>, val expectedPins: MutableList<String?>,
    )

    private class ScriptedTransport(
        private val key: String, private val deniedUri: String?, private val pauseRegistration: Boolean, private val inputAppId: String?, private val helloUuid: String?,
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
                "hello" -> incoming.send(JSONObject().put("type", "hello").put("payload", JSONObject()
                    .apply { if (helloUuid != null) put("deviceUUID", helloUuid) }).toString())
                "register" -> if (!pauseRegistration) incoming.send("""{"type":"registered","id":"$id","payload":{"client-key":"$key"}}""")
                "request" -> {
                    val payload = if (message.optString("uri") == LgProtocol.INPUT_LIST)
                        JSONObject().put("returnValue", true).put("devices", org.json.JSONArray()
                            .put(JSONObject().put("id", "HDMI_2"))
                            .put(JSONObject().put("id", "HDMI_3").apply { if (inputAppId != null) put("appId", inputAppId) })).toString()
                    else """{"returnValue":true}"""
                    incoming.send("""{"type":"response","id":"$id","payload":$payload}""")
                }
            }
        }
        override suspend fun receive(): String? = incoming.receiveCatching().getOrNull()
        override fun close() { closed = true; incoming.close() }
    }
}
