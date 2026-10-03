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
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var active = false
    private var lock: WifiManager.MulticastLock? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            _error.value = "Discovery failed ($errorCode); enter the host manually"
            stop()
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            _error.value = "Could not stop discovery ($errorCode)"
        }
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(this@NsdGoogleTvDiscovery) {
                if (!active) return
                pending.addLast(serviceInfo)
                resolveNext()
            }
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(this@NsdGoogleTvDiscovery) {
                _devices.value = _devices.value.filterNot { it.name == serviceInfo.serviceName }
            }
        }
    }

    @Synchronized override fun start() {
        if (active) return
        _devices.value = emptyList()
        _error.value = null
        active = true
        lock = wifi.createMulticastLock("MyRemoteGoogleTvDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (error: Exception) {
            active = false
            lock?.release()
            lock = null
            _error.value = error.message ?: "Discovery unavailable"
            throw error
        }
    }

    @Synchronized override fun stop() {
        if (!active) return
        active = false
        pending.clear()
        resolving = false
        runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        lock?.release()
        lock = null
    }

    @Synchronized private fun resolveNext() {
        if (!active || resolving || pending.isEmpty()) return
        val info = pending.removeFirst()
        resolving = true
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
                    GoogleTvDevice(serviceInfo.serviceName, it, serviceInfo.port, addresses)
                })
            }
            private fun finish(device: GoogleTvDevice?) {
                synchronized(this@NsdGoogleTvDiscovery) {
                    if (active && device != null) {
                        _devices.value = _devices.value.filterNot { it.name == device.name } + device
                    }
                    resolving = false
                    resolveNext()
                }
            }
        }) } catch (error: Exception) {
            _error.value = error.message ?: "Could not resolve device"
            resolving = false
            resolveNext()
        }
    }
}
