package com.myremote.app.google

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class DualStackSocketTest {
    @Test fun actualIpv4RefusalFallsBackToListeningIpv6Loopback() {
        val ipv4 = InetAddress.getByName("127.0.0.1")
        val ipv6 = InetAddress.getByName("::1")
        ServerSocket().use { server ->
            server.bind(InetSocketAddress(ipv6, 0))
            val tried = mutableListOf<InetAddress>()
            GoogleTvAddresses.firstConnected(listOf(ipv4, ipv6)) { address ->
                tried += address
                Socket().apply {
                    try { connect(InetSocketAddress(address, server.localPort), 1_000) }
                    catch (error: Exception) { close(); throw error }
                }
            }.use { client ->
                server.accept().use { accepted ->
                    client.outputStream.write(42)
                    accepted.soTimeout = 1_000
                    assertEquals(42, accepted.inputStream.read())
                    assertEquals(ipv6, client.inetAddress)
                    assertEquals(listOf(ipv4, ipv6), tried)
                }
            }
        }
    }
}
