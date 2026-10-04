package com.myremote.app.network

import java.net.InetAddress

/** Permit private LAN addresses and globally addressed hosts on a connected LAN prefix.
 * Never permits loopback, unspecified, multicast or a public WAN destination merely because
 * DNS/mDNS advertised it. TLS trust remains independent of this scope check.
 */
internal object LocalAddressPolicy {
    fun accepts(address: InetAddress, prefixes: List<Pair<InetAddress, Int>>): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val bytes = address.address
        if (bytes.size == 16 && bytes[0].toInt() and 0xfe == 0xfc) return true
        return prefixes.any { (local, bits) ->
            val other = local.address
            bytes.size == other.size && bits in 1..(bytes.size * 8) && (0 until bits).all { bit ->
                val mask = 1 shl (7 - bit % 8)
                bytes[bit / 8].toInt() and mask == other[bit / 8].toInt() and mask
            }
        }
    }
}
