package com.myremote.app.domain

import org.junit.Assert.*
import org.junit.Test

class ConnectionLifetimeTest {
    @Test fun repeatedVisibleServiceStartsDoNotReconnectLiveDevices() {
        var opens = 0; var closes = 0
        val lifetime = ConnectionLifetime({ opens++ }, { closes++ })
        assertFalse(lifetime.active.value)
        repeat(5) { lifetime.start() } // Open, return from another app, permission dialog, recreation.
        assertTrue(lifetime.active.value)
        assertEquals(1, opens)
        assertEquals(0, closes)
        lifetime.stop(); lifetime.stop()
        assertFalse(lifetime.active.value)
        assertEquals(1, closes)
        lifetime.start()
        assertEquals(2, opens)
    }

    @Test fun failedPartialStartReleasesResourcesAndCanBeRetried() {
        var opens = 0; var closes = 0
        val lifetime = ConnectionLifetime({ if (++opens == 1) error("Partial transport start") }, { closes++ })
        assertThrows(IllegalStateException::class.java) { lifetime.start() }
        assertFalse(lifetime.active.value)
        assertEquals(1, closes)
        lifetime.start()
        assertTrue(lifetime.active.value)
        lifetime.stop()
        assertEquals(2, closes)
    }
}
