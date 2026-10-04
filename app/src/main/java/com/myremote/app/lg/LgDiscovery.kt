package com.myremote.app.lg

import android.content.Context
import android.net.wifi.WifiManager
import com.myremote.app.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive

internal object LgSsdp {
    const val service = "urn:lge-com:service:webos-second-screen:1"
    val search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $service\r\n\r\n"

    fun headers(response: String): Map<String, String> = response.lineSequence().drop(1)
        .mapNotNull { line ->
            val index = line.indexOf(':')
            if (index < 1) null else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
        }.toMap()

    fun candidate(response: String, address: InetAddress): LgDevice? {
        if (!response.startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val headers = headers(response)
        if (!headers["st"].orEmpty().equals(service, ignoreCase = true) &&
            !headers["usn"].orEmpty().contains(service, ignoreCase = true)) return null
        val uuid = headers["usn"]?.substringBefore("::")?.takeIf(String::isNotBlank)
        return LgDevice("LG webOS TV", address.hostAddress ?: return null, uuid = uuid)
    }
}

interface LgDiscovery {
    val devices: StateFlow<List<LgDevice>>
    val error: StateFlow<String?>
    fun start()
    fun stop()
}

/** Bounded SSDP M-SEARCH, then optional UPnP description metadata from the responding host. */
class SsdpLgDiscovery(context: Context) : LgDiscovery, AutoCloseable {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val lan = com.myremote.app.network.LanNetwork(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _devices = MutableStateFlow<List<LgDevice>>(emptyList())
    override val devices: StateFlow<List<LgDevice>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error
    private var job: Job? = null
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private var generation = 0
    private var description: HttpURLConnection? = null

    @Suppress("DEPRECATION")
    @Synchronized override fun start() {
        if (job?.isActive == true) return
        _devices.value = emptyList()
        _error.value = null
        val runId = ++generation
        val ownedLock = wifi.createMulticastLock("CouchPilotLgDiscovery").apply {
            setReferenceCounted(false)
        }
        lock = ownedLock
        job = scope.launch {
            var ownedSocket: DatagramSocket? = null
            try {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                synchronized(this@SsdpLgDiscovery) {
                    if (generation != runId) return@launch
                    ownedLock.acquire()
                }
                DatagramSocket().use { active ->
                    ownedSocket = active
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    synchronized(this@SsdpLgDiscovery) {
                        if (generation != runId) return@launch
                        socket = active
                    }
                    val network = lan.selected() ?: throw java.io.IOException("No local network for LG discovery")
                    network.bindSocket(active)
                    active.soTimeout = 700
                    active.broadcast = true
                    val bytes = LgSsdp.search.toByteArray(Charsets.UTF_8)
                    val query = DatagramPacket(bytes, bytes.size,
                        InetAddress.getByName("239.255.255.250"), 1900)
                    active.send(query)
                    val deadline = System.nanoTime() + 4_000_000_000L
                    val seen = mutableSetOf<String>()
                    val buffer = ByteArray(4096)
                    while (System.nanoTime() < deadline && !active.isClosed && kotlinx.coroutines.currentCoroutineContext().isActive) {
                        val reply = DatagramPacket(buffer, buffer.size)
                        try { active.receive(reply) } catch (_: java.net.SocketTimeoutException) { continue }
                        if (runCatching { lan.localAddresses(network, listOf(reply.address)) }.isFailure) continue
                        val response = String(reply.data, 0, reply.length, Charsets.UTF_8)
                        val base = LgSsdp.candidate(response, reply.address) ?: continue
                        if (!seen.add(base.host)) continue
                        if (seen.size > 32) break
                        val described = describe(base, LgSsdp.headers(response)["location"], network, runId, deadline)
                        synchronized(this@SsdpLgDiscovery) {
                            if (generation == runId) _devices.value = _devices.value.filterNot {
                                (it.uuid != null && it.uuid == described.uuid) || it.host == described.host
                            } + described
                        }
                    }
                }
            } catch (error: Exception) {
                synchronized(this@SsdpLgDiscovery) {
                    if (generation == runId) _error.value = error.message ?: "LG discovery failed"
                }
            } finally {
                synchronized(this@SsdpLgDiscovery) {
                    if (generation == runId && _devices.value.isEmpty() && _error.value == null) {
                        _error.value = appContext.getString(R.string.lg_no_devices)
                    }
                    if (socket === ownedSocket) socket = null
                    if (lock === ownedLock) {
                        if (ownedLock.isHeld) ownedLock.release()
                        lock = null
                    }
                }
            }
        }
    }

    private fun describe(base: LgDevice, location: String?, network: android.net.Network?, runId: Int, deadline: Long): LgDevice {
        val url = LgDescription.location(base, location) ?: return base
        var connection: HttpURLConnection? = null
        return try {
            val active = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
            connection = active
            synchronized(this) {
                if (generation != runId) { active.disconnect(); return base }
                description = active
            }
            LgDescription.read(base, active) {
                synchronized(this) {
                    if (generation == runId) (deadline - System.nanoTime()) / 1_000_000 else 0
                }
            }
        } catch (_: Exception) { base }
        finally {
            connection?.disconnect()
            synchronized(this) { if (description === connection) description = null }
        }
    }

    @Synchronized override fun stop() {
        generation++
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        description?.disconnect()
        description = null
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    override fun close() { stop(); scope.cancel() }
}
