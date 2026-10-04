package com.myremote.app.google

import com.myremote.app.lg.LgPairingStoreTest
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class GoogleTvPersistenceTest {
    @Test fun latePairingCannotRestoreForgottenOrCancelledConfiguration() {
        val store = PairingStore(LgPairingStoreTest.MemoryPreferences())
        val device = GoogleTvDevice("TV", "192.0.2.8")
        val old = store.newPairingAttempt()
        store.clear()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { store.save(device, "old-pin", device.host, old) }
        assertNull(store.saved())
        val cancelled = store.newPairingAttempt()
        store.cancelPairingAttempt()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { store.save(device, "old-pin", device.host, cancelled) }
        val current = store.newPairingAttempt()
        store.save(device, "new-pin", device.host, current)
        assertEquals("new-pin", store.saved()!!.serverPin)
        store.clear()
        assertNull(store.saved())
    }

    @Test fun ipv6AlternatesAndAdvertisedPortSurviveRestartEvenOnPreHostnameApi() {
        val prefs = LgPairingStoreTest.MemoryPreferences()
        val addresses = listOf(InetAddress.getByName("192.0.2.8"), InetAddress.getByName("2001:db8::8"))
        PairingStore(prefs).save(GoogleTvDevice("TV", "192.0.2.8", 6543, addresses), "pin", "2001:db8::8")
        val saved = PairingStore(prefs).saved()!!
        assertEquals(addresses, saved.device.resolvedAddresses)
        assertEquals(6543, saved.device.port)
        assertEquals("192.0.2.8", saved.device.host)
        assertEquals("pin", saved.serverPin)
        assertEquals(addresses[1], GoogleTvAddresses.candidates(saved.device.host, saved.device.resolvedAddresses,
            saved.device.lastSuccessfulAddress) { throw java.net.UnknownHostException() }.first())
    }
}
