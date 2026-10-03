package com.myremote.app.google

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

/** Keep the service's addresses together: DNS/NSD ordering is not a reachability guarantee. */
internal object GoogleTvAddresses {
    fun candidates(
        host: String,
        discovered: List<InetAddress>,
        lastSuccessful: String?,
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    ): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        lastSuccessful?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }?.let(addresses::add)
        addresses.addAll(discovered)
        try {
            addresses.addAll(resolve(host))
        } catch (error: UnknownHostException) {
            if (addresses.isEmpty()) throw error
        }
        return addresses.distinctBy { it.hostAddress }
    }

    /** Only TCP connection failures advance to another address; TLS failures happen afterward. */
    fun <T> firstConnected(addresses: List<InetAddress>, connect: (InetAddress) -> T): T {
        require(addresses.isNotEmpty()) { "No device addresses" }
        val failures = mutableListOf<IOException>()
        for (address in addresses) {
            try {
                return connect(address)
            } catch (error: IOException) {
                failures += error
            }
        }
        throw IOException("Could not connect to any of ${addresses.size} device addresses: ${failures.last().message}",
            failures.last()).also { result -> failures.dropLast(1).forEach(result::addSuppressed) }
    }
}

/** Accept DNS names, IPv4, bare IPv6, scoped IPv6, and bracketed IPv6 literals. */
fun normalizedGoogleTvHost(input: String): String? {
    val trimmed = input.trim()
    val host = if (trimmed.startsWith("[") && trimmed.endsWith("]")) trimmed.drop(1).dropLast(1) else trimmed
    if (host.isEmpty() || host.length > 253) return null
    val allowed = if (':' in host) Regex("[A-Za-z0-9:.%_-]+") else Regex("[A-Za-z0-9._-]+")
    return host.takeIf(allowed::matches)
}
