package com.myremote.app.domain

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoppedCommandsTest {
    private fun stopped(action: RemoteAction) = runTest {
        var resources = 0; var executed = 0
        lateinit var scheduler: CommandScheduler
        val lifetime = ConnectionLifetime({ resources = 1 }, { resources = 0; scheduler.cancelAll() })
        scheduler = CommandScheduler(backgroundScope, { CommandDevice.SOUNDBAR }, { _, _ -> resources++; executed++ })
        lifetime.start(); lifetime.stop()
        assertFalse(lifetime.whileActive { scheduler.submit(action) })
        runCurrent() // Background/queued work cannot open anything.
        lifetime.stop(); runCurrent()
        assertEquals(0, resources); assertEquals(0, executed); assertFalse(lifetime.active.value)
        lifetime.start(); assertTrue(lifetime.whileActive { scheduler.submit(action) }); runCurrent()
        assertEquals(1, executed); assertTrue(lifetime.active.value)
        lifetime.stop(); scheduler.close(); assertEquals(0, resources)
    }
    @Test fun stoppedVolumeCannotReconnect() = stopped(RemoteAction.VolumeUp)
    @Test fun stoppedTvPowerCannotWakeOrReconnect() = stopped(RemoteAction.TvPower)
    @Test fun stoppedStreamerCannotOpenResources() = stopped(RemoteAction.Key(RemoteKey.HOME))
    @Test fun stoppedSourceCannotTriggerAncillaryWake() = stopped(RemoteAction.SelectInput(InputSource.XIAOMI))
    @Test fun stoppedLeaseCannotRestartFromForegroundWithoutExplicitResume() {
        var resources = 0
        val lifetime = ConnectionLifetime({ resources++ }, { resources = 0 })
        assertTrue(lifetime.mayAutoStart)
        lifetime.start(); lifetime.stop()
        assertFalse(lifetime.mayAutoStart)
        if (lifetime.mayAutoStart) lifetime.start()
        assertFalse(lifetime.active.value); assertEquals(0, resources)
        lifetime.start(); assertTrue(lifetime.active.value); assertTrue(lifetime.mayAutoStart)
        lifetime.stop()
    }

    @Test fun stoppedLifetimeStillCleansPassiveSetupResources() {
        var resources = 0
        val lifetime = ConnectionLifetime({}, { resources = 0 })
        resources = 1 // Discovery may be separately owned by a setup dialog.
        lifetime.stop(); assertEquals(0, resources); assertFalse(lifetime.active.value)
    }
}
