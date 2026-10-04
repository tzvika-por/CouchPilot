package com.myremote.app

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.hid.HidHost
import com.myremote.app.hid.HidStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters

/** Production Activity, service and native SDP; synthetic unbonded host, no physical peer/pairing. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BackgroundConnectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = context.applicationContext as RemoteApplication

    @After fun cleanup() = runBlocking {
        RemoteConnectionService.disconnect(context)
        instrumentation.runOnMainSync {
            app.remoteSession.stopConnections()
            app.remoteSession.useLanConnection(connect = false)
            HidStore(context).host(null)
        }
        await { !app.remoteSession.hidRegistered.value && !app.remoteSession.connectionSessionActive.value }
        if (Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun nativeHidRegistrationSurvivesActivityStopReturnAndRecreation() = runBlocking {
        permissions(notifications = true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await { app.remoteSession.connectionSessionActive.value }
            val original = app.remoteSession
            instrumentation.runOnMainSync {
                HidStore(context).host(HidHost("Synthetic test host", "02:00:00:00:00:01"))
                original.useBluetoothConnection()
            }
            await { original.hidRegistered.value }
            scenario.moveToState(Lifecycle.State.CREATED)
            // Give the OS UID-importance listener time to process the actual stopped Activity.
            delay(1_000)
            assertTrue(original.connectionSessionActive.value)
            assertTrue("Native keyboard registration must survive background", original.hidRegistered.value)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            delay(500)
            assertSame(original, app.remoteSession)
            assertTrue(original.hidRegistered.value)
            assertTrue(original.connectionSessionActive.value)
            RemoteConnectionService.disconnect(context)
            await { !original.connectionSessionActive.value && !original.hidRegistered.value }
        } finally { scenario.close() }
    }

    @Test fun deniedNotificationPermissionDoesNotPreventServiceOrExplicitDisconnect() = runBlocking {
        permissions(notifications = false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            await { app.remoteSession.connectionSessionActive.value }
            scenario.moveToState(Lifecycle.State.CREATED)
            delay(500)
            assertTrue(app.remoteSession.connectionSessionActive.value)
            RemoteConnectionService.disconnect(context)
            await { !app.remoteSession.connectionSessionActive.value }
        } finally { scenario.close() }
    }

    private fun permissions(notifications: Boolean) {
        if (Build.VERSION.SDK_INT >= 31)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) {
            if (notifications) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            else assertEquals("Start this suite with notification permission denied (adb pm revoke before instrumentation)",
                android.content.pm.PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
        context.getSharedPreferences("notification_consent", Context.MODE_PRIVATE).edit().putBoolean("asked", true).commit()
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(10_000) {
        while (!predicate()) delay(25)
    }
}
