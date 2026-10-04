package com.myremote.app.lg

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

internal object LgWakeOnLan {
    fun packet(mac: String): ByteArray {
        val parts = mac.split(':', '-')
        require(parts.size == 6 && parts.all { it.length == 2 && it.toIntOrNull(16) != null }) {
            "Invalid wake MAC address"
        }
        val address = parts.map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(102).also { packet ->
            packet.fill(0xff.toByte(), 0, 6)
            repeat(16) { index -> address.copyInto(packet, 6 + index * 6) }
        }
    }

    suspend fun send(context: Context, macs: List<String>) {
        val lan = com.myremote.app.network.LanNetwork(context)
        val network = lan.selected() ?: throw com.myremote.app.domain.DeviceFailure(
            com.myremote.app.domain.FailureKind.NETWORK, "No local network for LG wake")
        val (source, broadcast) = lan.ipv4(network) ?: throw com.myremote.app.domain.DeviceFailure(
            com.myremote.app.domain.FailureKind.NETWORK, "No IPv4 LAN broadcast for LG wake")
        sendPackets(macs, listOf(broadcast), createSocket = {
            DatagramSocket(null).apply {
                network.bindSocket(this)
                bind(java.net.InetSocketAddress(source, 0))
            }
        })
    }

    internal suspend fun sendPackets(
        macs: List<String>, destinations: List<InetAddress>, port: Int = 9,
        createSocket: () -> DatagramSocket = { DatagramSocket() },
    ) {
        require(macs.isNotEmpty()) { "No wake address configured for this LG TV" }
        require(destinations.isNotEmpty()) { "No wake destination" }
        val packets = macs.distinct().map(::packet)
        createSocket().use { socket ->
            socket.broadcast = true
            repeat(3) { attempt ->
                for (bytes in packets) for (destination in destinations) {
                    socket.send(DatagramPacket(bytes, bytes.size, destination, port))
                }
                if (attempt < 2) kotlinx.coroutines.delay(100)
            }
        }
    }
}
