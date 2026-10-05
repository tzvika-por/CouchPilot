package com.myremote.app.lg

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.network.LocalAddressPolicy
import java.io.IOException
import java.net.*
import javax.net.SocketFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class LgLiteralContainmentTest {
    private val prefixes = listOf(InetAddress.getByName("2001:db8:1::2") to 64)
    private fun access(sockets: RecordingSockets, addresses: List<InetAddress> = emptyList()) = LgNetworkAccess(
        sockets, { addresses }, { values -> values.filter { LocalAddressPolicy.accepts(it, prefixes) } })
    private fun rejected(host: String) = runBlocking {
        val sockets = RecordingSockets()
        val error = runCatching { OkHttpLgTransportFactory(networkAccess = access(sockets)).connect(host, null) }.exceptionOrNull()
        assertTrue("$host must fail the LAN policy, got $error", error is DeviceFailure)
        assertEquals("Rejected literal must not create even an unconnected socket", 0, sockets.created)
        assertTrue(sockets.destinations.isEmpty())
    }
    private fun accepted(host: String) = runBlocking {
        val sockets = RecordingSockets()
        val error = runCatching { OkHttpLgTransportFactory(networkAccess = access(sockets)).connect(host, null) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue("Accepted address must reach our non-networking socket probe", sockets.destinations.isNotEmpty())
    }
    @Test fun privateIpv4LiteralAccepted() = accepted("192.168.1.10")
    @Test fun publicIpv4RejectedBeforeSocket() = rejected("203.0.113.10")
    @Test fun loopbackIpv4RejectedBeforeSocket() = rejected("127.0.0.1")
    @Test fun unspecifiedIpv4RejectedBeforeSocket() = rejected("0.0.0.0")
    @Test fun multicastIpv4RejectedBeforeSocket() = rejected("224.0.0.1")
    @Test fun lanPrefixIpv6Accepted() = accepted("2001:db8:1::10")
    @Test fun uniqueLocalIpv6Accepted() = accepted("fd00::10")
    @Test fun linkLocalIpv6Accepted() = accepted("fe80::10")
    @Test fun loopbackIpv6RejectedBeforeSocket() = rejected("::1")
    @Test fun unspecifiedIpv6RejectedBeforeSocket() = rejected("::")
    @Test fun multicastIpv6RejectedBeforeSocket() = rejected("ff02::1")
    @Test fun globalIpv6OutsideLanRejectedBeforeSocket() = rejected("2001:db8:2::10")
    @Test fun mappedIpv6LoopbackRejectedBeforeSocket() = rejected("::ffff:127.0.0.1")
    @Test fun shortenedNumericIpv4CannotBypassPolicy() = rejected("127.1")
    @Test fun hostnameFiltersEveryResolvedAddress() = runBlocking {
        val sockets = RecordingSockets()
        val addresses = listOf("203.0.113.1", "::1", "2001:db8:2::2", "192.168.1.10").map(InetAddress::getByName)
        runCatching { OkHttpLgTransportFactory(networkAccess = access(sockets, addresses)).connect("tv.example", null) }
        assertEquals(listOf("192.168.1.10"), sockets.destinations.map { it.address.hostAddress }.distinct())
    }
    @Test fun hostnameWithNoLanAnswersNeverCreatesSocket() = runBlocking {
        val sockets = RecordingSockets()
        runCatching { OkHttpLgTransportFactory(networkAccess = access(sockets, listOf(InetAddress.getByName("203.0.113.1")))).connect("tv.example", null) }
        assertEquals(0, sockets.created)
    }
    @Test fun systemSocksProxyCannotBypassHostnameFiltering() = runBlocking {
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> = listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9)))
            override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) = Unit
        })
        try {
            val sockets = RecordingSockets()
            val error = runCatching { OkHttpLgTransportFactory(networkAccess = access(sockets,
                listOf(InetAddress.getByName("203.0.113.1")))).connect("tv.example", null) }.exceptionOrNull()
            assertTrue(error is DeviceFailure); assertEquals(0, sockets.created)
        } finally { ProxySelector.setDefault(previous) }
    }

    @Test fun actualOkHttp412NumericRouteBypassesDnsCallback() {
        val sockets = RecordingSockets(); var lookups = 0
        val client = OkHttpClient.Builder().socketFactory(sockets).dns(object : okhttp3.Dns { override fun lookup(hostname: String): List<InetAddress> { lookups++; error("DNS should be bypassed") } }).build()
        try {
            runCatching { client.newCall(Request.Builder().url("http://127.0.0.1:3001/").build()).execute() }
            assertEquals(0, lookups); assertTrue(sockets.created > 0)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    private class RecordingSockets : SocketFactory() {
        var created = 0
        val destinations = mutableListOf<InetSocketAddress>()
        override fun createSocket(): Socket { created++; return object : Socket() {
            override fun connect(endpoint: SocketAddress, timeout: Int) { destinations += endpoint as InetSocketAddress; throw IOException("Probe: no real socket") }
        } }
        override fun createSocket(host: String, port: Int) = createSocket()
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = createSocket()
        override fun createSocket(host: InetAddress, port: Int) = createSocket()
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = createSocket()
    }
}
