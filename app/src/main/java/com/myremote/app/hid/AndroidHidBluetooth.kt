package com.myremote.app.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Native phone-to-TV HID only. No reflection, scan, audio profile or process/network changes. */
class AndroidHidBluetooth(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun canAdvertise() = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    val supported get() = Build.VERSION.SDK_INT >= 28 && adapter != null

    @SuppressLint("MissingPermission")
    fun pairedHosts(): List<HidHost> {
        if (!hasPermission()) return emptyList()
        return adapter?.bondedDevices.orEmpty().filter {
            val name = it.name.orEmpty()
            name.contains("Xiaomi", true) || name.contains("MiTV", true) || name.contains("TV Box", true) ||
                name.contains("Google TV", true)
        }.map { HidHost(it.name ?: "TV", it.address) }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    fun phoneName(): String? = if (hasPermission()) adapter?.name else null

    @SuppressLint("MissingPermission")
    internal val factory = HidTransportFactory { host ->
        if (Build.VERSION.SDK_INT < 28) throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth remote requires Android 9 or later")
        if (!hasPermission()) throw DeviceFailure(FailureKind.PERMISSION_DENIED, "Bluetooth permission required")
        val bt = adapter ?: throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is unavailable")
        if (!bt.isEnabled) throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth is off")
        require(BluetoothAdapter.checkBluetoothAddress(host.address))
        val transport = NativeTransport(context, bt, host)
        try { transport.start(); transport.registration.await(); transport }
        catch (error: Throwable) { transport.close(); throw error }
    }

    @RequiresApi(28)
    @SuppressLint("MissingPermission") // Factory checks CONNECT; revocation surfaces to controller and cleanup.
    private class NativeTransport(
        private val context: Context,
        private val adapter: BluetoothAdapter,
        private val host: HidHost,
    ) : HidTransport {
        private val closed = AtomicBoolean()
        private val lifetime = Any()
        private val executor = Executors.newSingleThreadExecutor()
        private val eventsChannel = Channel<HidEvent>(Channel.UNLIMITED)
        override val events = eventsChannel.receiveAsFlow()
        val registration = CompletableDeferred<Unit>()
        @Volatile private var profile: BluetoothHidDevice? = null
        @Volatile private var connected = false
        private val reports = HidReports()
        @Volatile override var failure: FailureKind? = null
            private set
        override val bonded get() = adapter.bondedDevices.any { matches(it) }
        private fun matches(device: BluetoothDevice) = device.address.equals(host.address, true)
        private fun reject(device: BluetoothDevice) { runCatching { profile?.disconnect(device) } }

        private val callback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                if (closed.get()) return
                if (!registered) {
                    registration.completeExceptionally(DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth remote registration was lost"))
                    eventsChannel.trySend(HidEvent.DISCONNECTED)
                    return
                }
                pluggedDevice?.takeUnless(::matches)?.let(::reject)
                registration.complete(Unit)
                // An unbonded host pairs by finding this phone in its normal accessory UI.
                if (bonded) {
                    val target = adapter.getRemoteDevice(host.address)
                    when (profile?.getConnectionState(target)) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            connected = true
                            eventsChannel.trySend(HidEvent.CONNECTED)
                        }
                        BluetoothProfile.STATE_CONNECTING -> Unit
                        else -> if (profile?.connect(target) != true) eventsChannel.trySend(HidEvent.DISCONNECTED)
                    }
                }
            }
            override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
                if (closed.get()) return
                if (!matches(device)) { reject(device); return }
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connected = true
                        reports.clear()
                        eventsChannel.trySend(HidEvent.CONNECTED)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connected = false
                        reports.clear()
                        eventsChannel.trySend(HidEvent.DISCONNECTED)
                    }
                }
            }
            override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
                if (closed.get()) return
                if (!matches(device)) { reject(device); return }
                val bytes = reports.get(type.toInt(), id.toInt(), bufferSize)
                if (bytes == null) {
                    profile?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
                } else profile?.replyReport(device, type, id, bytes)
            }
            override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
                if (closed.get()) return
                if (!matches(device)) { reject(device); return }
                // Keyboard LED output is allowed; it never becomes a control command.
                val valid = reports.set(type.toInt(), id.toInt(), data)
                profile?.reportError(device, if (valid) BluetoothHidDevice.ERROR_RSP_SUCCESS else BluetoothHidDevice.ERROR_RSP_INVALID_PARAM)
            }
            override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
                if (!closed.get() && matches(device) && protocol != BluetoothHidDevice.PROTOCOL_REPORT_MODE)
                    profile?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
            override fun onVirtualCableUnplug(device: BluetoothDevice) {
                if (!closed.get() && matches(device)) eventsChannel.trySend(HidEvent.DISCONNECTED)
            }
        }

        private val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(kind: Int, proxy: BluetoothProfile) = synchronized(lifetime) {
                if (kind != BluetoothProfile.HID_DEVICE) return@synchronized
                if (closed.get()) { adapter.closeProfileProxy(kind, proxy); return@synchronized }
                val hid = proxy as BluetoothHidDevice
                profile = hid
                val sdp = BluetoothHidDeviceAppSdpSettings("My Remote", "TV remote", "My Remote", BluetoothHidDevice.SUBCLASS1_NONE, HidProtocol.descriptor)
                try {
                    val sent = hid.registerApp(sdp, null, null, { task ->
                        if (!closed.get()) runCatching { executor.execute {
                            if (!closed.get()) try { task.run() } catch (error: Exception) {
                                failure = com.myremote.app.domain.failureKind(error)
                                registration.completeExceptionally(error)
                                eventsChannel.trySend(HidEvent.DISCONNECTED)
                            }
                        } }
                    }, callback)
                    if (!sent) registration.completeExceptionally(DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth HID profile is unavailable or in use"))
                } catch (error: Exception) { registration.completeExceptionally(error) }
                Unit
            }
            override fun onServiceDisconnected(kind: Int) {
                if (kind == BluetoothProfile.HID_DEVICE && !closed.get()) {
                    registration.completeExceptionally(DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth service disconnected"))
                    eventsChannel.trySend(HidEvent.DISCONNECTED)
                }
            }
        }
        fun start() {
            if (!adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE))
                throw DeviceFailure(FailureKind.UNAVAILABLE, "Bluetooth HID is not supported by this phone")
        }
        override fun send(report: HidReport) {
            if (closed.get() || !connected) throw DeviceFailure(FailureKind.NOT_CONNECTED, "Bluetooth host is not connected")
            // Record before send so a concurrent GET_REPORT cannot synthesize an early key-up.
            reports.record(report)
            if (profile?.sendReport(adapter.getRemoteDevice(host.address), report.id, report.bytes) != true)
                throw DeviceFailure(FailureKind.NETWORK, "Bluetooth report was not accepted")
        }
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            connected = false
            val attached = synchronized(lifetime) { profile.also { profile = null } }
            attached?.let { hid ->
                for (id in 1..3) runCatching {
                    hid.sendReport(adapter.getRemoteDevice(host.address), id, requireNotNull(HidProtocol.released(id)).bytes)
                }
                runCatching { hid.disconnect(adapter.getRemoteDevice(host.address)) }
                runCatching { hid.unregisterApp() }
                runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
            }
            profile = null
            registration.cancel()
            eventsChannel.close()
            executor.shutdownNow()
        }
    }
}
