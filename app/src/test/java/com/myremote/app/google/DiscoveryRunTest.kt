package com.myremote.app.google

import org.junit.Assert.*
import org.junit.Test

class DiscoveryRunTest {
    @Test fun stoppedRunCannotRestoreDeviceOrAffectRestartedDiscovery() {
        val runs = DiscoveryRun()
        val first = runs.start()
        val stale = runs.found(first, "Xiaomi")!!
        runs.stop()
        val second = runs.start()
        val live = runs.found(second, "Xiaomi")!!
        assertFalse(runs.accepts("Xiaomi", stale))
        assertNull(runs.found(first, "Other TV"))
        runs.lost(first, "Xiaomi")
        assertTrue(runs.accepts("Xiaomi", live))
    }
    @Test fun lostServiceCannotBeResurrectedByLateResolveEvenIfFoundAgain() {
        val runs = DiscoveryRun()
        val run = runs.start()
        val oldAppearance = runs.found(run, "Xiaomi")!!
        runs.lost(run, "Xiaomi")
        assertFalse(runs.accepts("Xiaomi", oldAppearance))
        val newAppearance = runs.found(run, "Xiaomi")!!
        assertFalse(runs.accepts("Xiaomi", oldAppearance))
        assertTrue(runs.accepts("Xiaomi", newAppearance))
    }
}
