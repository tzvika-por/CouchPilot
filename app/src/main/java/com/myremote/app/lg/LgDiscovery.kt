package com.myremote.app.lg

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.myremote.app.R
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _devices = MutableStateFlow<List<LgDevice>>(emptyList())
    override val devices: StateFlow<List<LgDevice>> = _devices
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error
    private var job: Job? = null
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private var generation = 0

    @Suppress("DEPRECATION")
    @Synchronized override fun start() {
        if (job?.isActive == true) return
        _devices.value = emptyList()
        _error.value = null
        val runId = ++generation
        val ownedLock = wifi.createMulticastLock("MyRemoteLgDiscovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        lock = ownedLock
        job = scope.launch {
            var ownedSocket: DatagramSocket? = null
            try {
                DatagramSocket().use { active ->
                    ownedSocket = active
                    synchronized(this@SsdpLgDiscovery) { socket = active }
                    connectivity.allNetworks.firstOrNull { network ->
                        connectivity.getNetworkCapabilities(network)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    }?.bindSocket(active)
                    active.soTimeout = 700
                    active.broadcast = true
                    val bytes = LgSsdp.search.toByteArray(Charsets.UTF_8)
                    val query = DatagramPacket(bytes, bytes.size,
                        InetAddress.getByName("239.255.255.250"), 1900)
                    active.send(query)
                    val deadline = System.currentTimeMillis() + 4_000
                    while (System.currentTimeMillis() < deadline && !active.isClosed) {
                        val buffer = ByteArray(4096)
                        val reply = DatagramPacket(buffer, buffer.size)
                        try { active.receive(reply) } catch (_: java.net.SocketTimeoutException) { continue }
                        val response = String(reply.data, 0, reply.length, Charsets.UTF_8)
                        val base = LgSsdp.candidate(response, reply.address) ?: continue
                        val described = describe(base, LgSsdp.headers(response)["location"])
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
                        ownedLock.release()
                        lock = null
                    }
                }
            }
        }
    }

    private fun describe(base: LgDevice, location: String?): LgDevice {
        if (location.isNullOrBlank()) return base
        return runCatching {
            val uri = URI(location)
            if (uri.scheme !in listOf("http", "https") || uri.host != base.host) return base
            val connection = URL(location).openConnection() as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_200
            try {
                connection.inputStream.use { stream ->
                    val output = ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    while (output.size() <= 65_536) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        output.write(chunk, 0, count)
                    }
                    val bytes = output.toByteArray()
                    if (bytes.size > 65_536) throw IOException("LG description too large")
                    val factory = DocumentBuilderFactory.newInstance().apply {
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        setFeature("http://xml.org/sax/features/external-general-entities", false)
                        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    }
                    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
                    fun value(tag: String): String? = document.getElementsByTagName(tag).item(0)
                        ?.textContent?.trim()?.takeIf(String::isNotBlank)
                    base.copy(name = value("friendlyName") ?: base.name,
                        model = value("modelName"), uuid = value("UDN") ?: base.uuid)
                }
            } finally { connection.disconnect() }
        }.getOrDefault(base)
    }

    @Synchronized override fun stop() {
        generation++
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        lock?.release()
        lock = null
    }

    override fun close() { stop(); scope.cancel() }
}
