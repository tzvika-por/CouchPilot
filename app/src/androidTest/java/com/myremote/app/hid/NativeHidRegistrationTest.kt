package com.myremote.app.hid

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Real public profile/SDP registration on the test device; no radio peer or bond requested. */
class NativeHidRegistrationTest {
    @Test fun publicAdapterRegistersKeyboardServiceWithoutInitiatingPairing() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 31) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_CONNECT)
        val adapter = requireNotNull(context.getSystemService(BluetoothManager::class.java).adapter)
        assertTrue("Test emulator Bluetooth must be enabled", adapter.isEnabled)
        val host = HidHost("Synthetic test host", "02:00:00:00:00:01")
        assertFalse(adapter.bondedDevices.any { it.address == host.address })
        val bluetooth = AndroidHidBluetooth(context)
        val transport = withTimeout(10_000) { bluetooth.factory.open(host) }
        try {
            assertFalse(transport.bonded)
            assertNull(transport.failure)
            assertFalse(adapter.bondedDevices.any { it.address == host.address })
        } finally { transport.close() }
    }
}
