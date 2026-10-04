package com.myremote.app.samsung

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SamsungDevice(val name: String, val address: String)

class SamsungBluetooth(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<SamsungDevice> {
        if (!hasPermission()) throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Bluetooth permission required")
        val bt = adapter ?: throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is unavailable")
        if (!bt.isEnabled) throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is off")
        return bt.bondedDevices.map { SamsungDevice(it.name ?: "Bluetooth device", it.address) }
            .filter { it.name.contains("samsung", true) || it.name.contains("soundbar", true) || it.name.contains("M360", true) }
            .sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    internal val transportFactory = SamsungTransportFactory { address ->
        if (!hasPermission()) throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Bluetooth permission required")
        val bt = adapter ?: throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is unavailable")
        if (!bt.isEnabled) throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is off")
        val device = bt.bondedDevices.firstOrNull { it.address == address }
            ?: throw DeviceFailure(FailureKind.NOT_CONNECTED, "Soundbar must already be paired in Android")
        // Vendor uses the public insecure RFCOMM API. Android bonding and SDP resolve the channel.
        // No hidden channel reflection, A2DP connection, media routing, input switch, or BLE scan.
        val socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(SamsungProtocol.SERVICE_UUID))
        try {
            kotlinx.coroutines.withTimeout(10_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                    val worker = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            socket.connect()
                            continuation.resume(Unit) { _, _, _ -> runCatching { socket.close() } }
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                        }
                    }
                    // Closing the native socket unblocks connect on cancellation/timeout.
                    continuation.invokeOnCancellation { runCatching { socket.close() }; worker.cancel() }
                }
            }
            object : SamsungTransport {
                override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
                    socket.outputStream.write(bytes)
                    socket.outputStream.flush()
                }
                override suspend fun receive(): SamsungProtocol.Frame? = withContext(Dispatchers.IO) {
                    SamsungProtocol.read(socket.inputStream)
                }
                override fun close() { runCatching { socket.close() } }
            }
        } catch (error: Throwable) {
            runCatching { socket.close() }
            if (error is kotlinx.coroutines.TimeoutCancellationException)
                throw DeviceFailure(FailureKind.NETWORK, "Samsung connection timed out", error)
            throw error
        }
    }
}
