package com.myremote.app.google

import org.junit.Assert.*
import org.junit.Test

class DiscoveryRunTest {
    @Test fun restartingScanWaitsForOldResolveAndStaleCallbackCannotReleaseNewOne() {
        val runs = DiscoveryRun()
        val slot = DiscoveryResolution()
        val first = runs.start()
        val old = runs.found(first, "TV")!!
        slot.begin(old)
        runs.stop()
        val restarted = runs.start()
        val live = runs.found(restarted, "TV")!!
        assertTrue(slot.busy) // stopServiceDiscovery does not stop legacy resolveService.
        assertFalse(runs.accepts("TV", old))
        assertTrue(slot.finish(old))
        slot.begin(live)
        assertFalse(slot.finish(old))
        assertTrue(slot.busy)
        assertTrue(slot.finish(live))
        assertFalse(slot.busy)
        assertTrue(runs.accepts("TV", live))
    }

    @Test fun duplicateAndFloodedAdvertisementsStayBounded() {
        val runs = DiscoveryRun()
        val run = runs.start()
        val first = runs.found(run, "TV 0")!!
        repeat(100) { assertEquals(first, runs.found(run, "TV 0")) }
        for (index in 1 until 64) assertNotNull(runs.found(run, "TV $index"))
        assertNull(runs.found(run, "Excess TV"))
        runs.lost(run, "TV 0")
        assertNotNull(runs.found(run, "Excess TV"))
        assertFalse(runs.accepts("TV 0", first))
    }

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
