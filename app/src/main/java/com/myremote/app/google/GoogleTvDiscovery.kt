package com.myremote.app.google

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GoogleTvDevice(
    val name: String,
    val host: String,
    val port: Int = 6466,
    val resolvedAddresses: List<InetAddress> = emptyList(),
    val lastSuccessfulAddress: String? = null,
    val network: android.net.Network? = null,
) {
    val pairingPort: Int get() = 6467
}

interface GoogleTvDiscovery {
    val devices: StateFlow<List<GoogleTvDevice>>
    val error: StateFlow<String?>
    fun start()
    fun stop()
}

/** NSD callbacks are confined by Android to this listener; no Activity is retained. */
class NsdGoogleTvDiscovery(context: Context) : GoogleTvDiscovery {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _devices = MutableStateFlow<List<GoogleTvDevice>>(emptyList())
    override val devices: StateFlow<List<GoogleTvDevice>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error
    private val pending = ArrayDeque<Pair<NsdServiceInfo, DiscoveryRun.Token>>()
    private val run = DiscoveryRun()
    private val resolution = DiscoveryResolution()
    private var active = false
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null

    private fun listener(runId: Int) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            synchronized(this@NsdGoogleTvDiscovery) {
                if (!run.current(runId) || !active) return
                _error.value = "Discovery failed ($errorCode); enter the host manually"
                stop()
            }
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (run.current(runId)) _error.value = "Could not stop discovery ($errorCode)"
        }
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(this@NsdGoogleTvDiscovery) {
                if (!active || !run.current(runId)) return
                val token = run.found(runId, serviceInfo.serviceName) ?: return
                if (resolution.owns(token) || _devices.value.any { it.name == serviceInfo.serviceName }) return
                if (pending.any { it.first.serviceName == serviceInfo.serviceName }) return
                pending.addLast(serviceInfo to token)
                resolveNext()
            }
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(this@NsdGoogleTvDiscovery) {
                if (!active || !run.current(runId)) return
                run.lost(runId, serviceInfo.serviceName)
                pending.removeAll { it.first.serviceName == serviceInfo.serviceName }
                _devices.value = _devices.value.filterNot { it.name == serviceInfo.serviceName }
            }
        }
    }

    @Synchronized override fun start() {
        if (active) return
        _devices.value = emptyList()
        _error.value = null
        active = true
        discoveryListener = listener(run.start())
        try {
            lock = wifi.createMulticastLock("MyRemoteGoogleTvDiscovery").apply { setReferenceCounted(false) }
            lock?.acquire()
            nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, requireNotNull(discoveryListener))
        } catch (error: Exception) {
            active = false
            run.stop()
            lock?.takeIf { it.isHeld }?.release()
            lock = null
            _error.value = error.message ?: "Discovery unavailable"
            throw error
        }
    }

    @Synchronized override fun stop() {
        if (!active) return
        active = false
        run.stop()
        pending.clear()
        discoveryListener?.let { owned -> runCatching { nsd.stopServiceDiscovery(owned) } }
        discoveryListener = null
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    @Synchronized private fun resolveNext() {
        if (!active || resolution.busy || pending.isEmpty()) return
        val (info, token) = pending.removeFirst()
        val runId = token.generation
        resolution.begin(token)
        @Suppress("DEPRECATION")
        try { nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finish(null)
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= 34) serviceInfo.hostAddresses else
                    listOfNotNull(serviceInfo.host)
                @Suppress("DEPRECATION")
                val host = if (Build.VERSION.SDK_INT >= 36) {
                    serviceInfo.hostname?.trimEnd('.')?.let { name ->
                        if (name.endsWith(".local", ignoreCase = true)) name else "$name.local"
                    }
                } else null
                val endpoint = host ?: addresses.firstOrNull()?.hostAddress
                finish(endpoint?.let {
                    GoogleTvDevice(serviceInfo.serviceName, it, serviceInfo.port, addresses, network = if (Build.VERSION.SDK_INT >= 33) serviceInfo.network else null)
                })
            }
            private fun finish(device: GoogleTvDevice?) {
                synchronized(this@NsdGoogleTvDiscovery) {
                    if (!resolution.finish(token)) return
                    if (!active || !run.current(runId)) { resolveNext(); return }
                    if (device != null && run.accepts(info.serviceName, token)) {
                        _devices.value = _devices.value.filterNot { it.name == device.name } + device
                    }
                    resolveNext()
                }
            }
        }) } catch (error: Exception) {
            _error.value = error.message ?: "Could not resolve device"
            resolution.finish(token)
            resolveNext()
        }
    }
}
