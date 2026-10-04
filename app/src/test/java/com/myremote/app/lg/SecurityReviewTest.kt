package com.myremote.app.lg

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import com.myremote.app.domain.isTlsIdentityFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SecurityReviewTest {
    private val device = LgDevice("LG TV", "127.0.0.1")

    @Test fun encryptedCredentialMigrationRetainsGrantAndPinWithoutPlaintext() {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        prefs.edit().putString("host", device.host).putString("name", device.name)
            .putString("client_key", "legacy-key").putString("certificate_pin", "trusted-pin").commit()
        val store = LgPairingStore(prefs, testLgCipher())
        assertEquals("legacy-key", store.read()!!.clientKey)
        assertFalse(prefs.contains("client_key"))
        assertNotEquals("legacy-key", prefs.getString("client_key_encrypted", null))
        assertEquals("trusted-pin", LgPairingStore(prefs, testLgCipher()).read()!!.certificatePin)
        store.clearAuthorization()
        assertFalse(prefs.contains("client_key_encrypted"))
        assertEquals("trusted-pin", store.read()!!.certificatePin)
    }

    @Test fun credentialEncryptionUsesFreshIvAndRejectsTamperingWithoutLosingDevice() {
        val cipher = testLgCipher()
        val first = cipher.encrypt("key")
        assertNotEquals(first, cipher.encrypt("key"))
        assertEquals("key", cipher.decrypt(first))
        val bytes = java.util.Base64.getDecoder().decode(first)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val corrupt = java.util.Base64.getEncoder().encodeToString(bytes)
        assertThrows(java.security.GeneralSecurityException::class.java) { cipher.decrypt(corrupt) }
        val prefs = LgPairingStoreTest.MemoryPreferences()
        val store = LgPairingStore(prefs, cipher)
        store.select(device); store.registered("key", "pin")
        prefs.edit().putString("client_key_encrypted", corrupt).commit()
        val saved = store.read()!!
        assertNull(saved.clientKey)
        assertTrue(saved.authorizationNeedsRefresh)
        assertEquals(device.host, saved.device.host)
        assertEquals("pin", saved.certificatePin)
    }

    @Test fun unavailableKeystoreDoesNotExposeLegacyGrantOrCrashConfigurationRead() {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        prefs.edit().putString("host", device.host).putString("client_key", "legacy-key")
            .putString("certificate_pin", "pin").commit()
        val store = LgPairingStore(prefs, LgCredentialCipher { throw java.security.ProviderException("Unavailable") })
        assertNull(store.read()!!.clientKey)
        assertTrue(store.read()!!.authorizationNeedsRefresh)
        assertEquals("pin", store.read()!!.certificatePin)
        // A temporary provider failure must not destroy the stored grant. Retry can still migrate it.
        assertEquals("legacy-key", LgPairingStore(prefs, testLgCipher()).read()!!.clientKey)
        assertFalse(prefs.contains("client_key"))
    }

    @Test fun optionalDescriptionCannotReplaceAdvertisedServiceIdentity() {
        val advertised = device.copy(uuid = "uuid:service-identity")
        val described = LgDescription.parse(advertised,
            "<root><friendlyName>Living room</friendlyName><UDN>uuid:other-root-device</UDN></root>".toByteArray())
        assertEquals(advertised.uuid, described.uuid)
        assertEquals("Living room", described.name)
    }

    @Test fun metadataAllowsOnlyRespondingHostWithoutCredentials() {
        assertNotNull(LgDescription.location(device, "http://127.0.0.1:1234/device.xml"))
        assertNull(LgDescription.location(device, "http://example.com/device.xml"))
        assertNull(LgDescription.location(device, "http://user:secret@127.0.0.1/device.xml"))
        assertNull(LgDescription.location(device, "file:///etc/passwd"))
        assertNotNull(LgDescription.location(device.copy(host = "2001:db8::1"), "http://[2001:db8::1]/device.xml"))
    }

    @Test fun realHttpRedirectDoesNotContactOtherEndpoint() {
        MockWebServer().use { first -> MockWebServer().use { destination ->
            first.start(); destination.start()
            first.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination.url("/forbidden")))
            destination.enqueue(MockResponse().setBody("<root/>"))
            val connection = first.url("/description").toUrl().openConnection() as HttpURLConnection
            assertEquals(device, LgDescription.read(device, connection) { 1_200 })
            assertEquals(1, first.requestCount)
            assertEquals(0, destination.requestCount)
        } }
    }

    @Test fun descriptionRejectsOversizeAndExternalEntities() {
        assertThrows(IllegalArgumentException::class.java) { LgDescription.parse(device, ByteArray(65_537)) }
        val evil = """<!DOCTYPE root [<!ENTITY leak SYSTEM "file:///etc/passwd">]><root><friendlyName>&leak;</friendlyName></root>"""
        assertThrows(Exception::class.java) { LgDescription.parse(device, evil.toByteArray()) }
        val safe = LgDescription.parse(device, "<root><friendlyName>Living room</friendlyName><modelNumber>LG</modelNumber></root>".toByteArray())
        assertEquals("Living room", safe.name)
        assertEquals("LG", safe.model)
    }

    @Test fun metadataRejectsExcessiveNestingAndEncodingBypasses() {
        val deep = "<root>" + "<nested>".repeat(40) + "</nested>".repeat(40) + "</root>"
        assertThrows(Exception::class.java) { LgDescription.parse(device, deep.toByteArray()) }
        val dtd = "<!DOCTYPE root [<!ENTITY leak SYSTEM 'file:///etc/passwd'>]><root/>"
        assertThrows(Exception::class.java) { LgDescription.parse(device, dtd.toByteArray(Charsets.UTF_16)) }
        assertThrows(Exception::class.java) { LgDescription.parse(device, byteArrayOf(0xff.toByte(), 0xfe.toByte())) }
    }

    @Test fun expiredDiscoveryBudgetDisconnectsDescription() {
        var disconnected = false
        val connection = object : HttpURLConnection(URL("http://127.0.0.1/")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected = true }
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream("<root/>".toByteArray())
        }
        var checks = 0
        assertThrows(IOException::class.java) {
            LgDescription.read(device, connection) { if (++checks < 4) 100 else 0 }
        }
        assertTrue(disconnected)
    }

    @Test fun requestCapacityIsReleasedByRepliesCancellationAndDisconnect() {
        val requests = LgRequests()
        val pending = List(32) { requests.open() }
        assertThrows(IOException::class.java) { requests.open() }
        requests.remove(pending[0].first)
        val replacement = requests.open()
        assertTrue(requests.complete(LgMessage("response", replacement.first, null, null)))
        assertFalse(requests.complete(LgMessage("response", replacement.first, null, null)))
        requests.failAll(IOException("Disconnected"))
        assertTrue(pending.all { it.second.isCompleted })
        assertNotNull(requests.open())
    }

    @Test fun certificateRejectionStopsControllerRetriesAndRetainsGrant() = runTest {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        val store = LgPairingStore(prefs, testLgCipher())
        store.select(device); store.registered("existing-key", "existing-pin")
        var connections = 0
        val factory = object : LgTransportFactory {
            override suspend fun connect(host: String, expectedPin: String?): LgTransport {
                connections++
                throw SSLHandshakeException("Rejected certificate").apply { initCause(CertificateException("Changed")) }
            }
        }
        val discovery = object : LgDiscovery {
            override val devices = MutableStateFlow(emptyList<LgDevice>())
            override val error = MutableStateFlow<String?>(null)
            override fun start() = Unit
            override fun stop() = Unit
        }
        val controller = LgTvController(store, factory, discovery,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))) { }
        controller.connectStored(); runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(1, connections)
        assertEquals(com.myremote.app.domain.ConnectionState.ERROR, controller.connectionState)
        assertEquals("existing-key", store.read()!!.clientKey)
        assertEquals("existing-pin", store.read()!!.certificatePin)
        controller.close()
    }

    @Test fun certificateClassificationDoesNotStopTransientStreamRecovery() {
        assertTrue(isTlsIdentityFailure(SSLHandshakeException("Handshake").apply { initCause(CertificateException("Expired")) }))
        assertFalse(isTlsIdentityFailure(javax.net.ssl.SSLException("Broken connection")))
        assertFalse(isTlsIdentityFailure(java.net.SocketTimeoutException()))
    }
}
