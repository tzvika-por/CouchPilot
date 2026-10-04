package com.myremote.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/** Socket-local selection only: never bind the Android process or change routes/VPN settings. */
class LanNetwork(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    @Suppress("DEPRECATION")
    fun selected(preferred: Network? = null): Network? {
        fun usable(network: Network): Boolean {
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
        return preferred?.takeIf(::usable) ?: manager.activeNetwork?.takeIf(::usable)
            ?: manager.allNetworks.firstOrNull(::usable)
    }

    fun localAddresses(network: Network, addresses: List<InetAddress>): List<InetAddress> {
        val prefixes = manager.getLinkProperties(network)?.linkAddresses.orEmpty().map { it.address to it.prefixLength }
        return addresses.filter { LocalAddressPolicy.accepts(it, prefixes) }.takeIf { it.isNotEmpty() }
            ?: throw com.myremote.app.domain.DeviceFailure(com.myremote.app.domain.FailureKind.NETWORK,
                "No local device address on the selected LAN")
    }

    fun ipv4(network: Network): Pair<Inet4Address, Inet4Address>? =
        manager.getLinkProperties(network)?.linkAddresses?.firstNotNullOfOrNull { link ->
            val address = link.address as? Inet4Address ?: return@firstNotNullOfOrNull null
            if (link.prefixLength !in 1..30) return@firstNotNullOfOrNull null
            address to broadcast(address, link.prefixLength)
        }

    internal companion object {
        fun broadcast(address: Inet4Address, prefix: Int): Inet4Address {
            require(prefix in 1..30)
            var value = 0L
            address.address.forEach { value = (value shl 8) or (it.toLong() and 255) }
            val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            val broadcast = value or (mask xor 0xffffffffL)
            return InetAddress.getByAddress(ByteArray(4) { index ->
                (broadcast ushr (24 - index * 8)).toByte()
            }) as Inet4Address
        }
    }
}
