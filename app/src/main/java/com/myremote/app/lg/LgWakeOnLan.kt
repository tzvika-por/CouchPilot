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

    @Suppress("DEPRECATION")
    fun send(context: Context, macs: List<String>) {
        require(macs.isNotEmpty()) { "No wake address configured for this LG TV" }
        val destinations = buildSet {
            add(InetAddress.getByName("255.255.255.255"))
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { networkInterface ->
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    networkInterface.interfaceAddresses.mapNotNull { it.broadcast }
                        .filterIsInstance<Inet4Address>().forEach(::add)
                }
            }
        }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            if (wifiNetwork != null) wifiNetwork.bindSocket(socket)
            for (mac in macs) for (destination in destinations) {
                val bytes = packet(mac)
                socket.send(DatagramPacket(bytes, bytes.size, destination, 9))
            }
        }
    }
}
