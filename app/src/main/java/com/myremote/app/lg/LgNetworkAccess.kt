package com.myremote.app.lg

import com.myremote.app.domain.DeviceFailure
import com.myremote.app.domain.FailureKind
import com.myremote.app.network.LocalAddressPolicy
import java.net.InetAddress
import javax.net.SocketFactory

/** Production uses the selected LAN's socket factory, DNS and connected-prefix policy.
 * Explicit injection is for isolated protocol tests; defaults still enforce LAN-only scope.
 */
internal class LgNetworkAccess(
    val socketFactory: SocketFactory = SocketFactory.getDefault(),
    val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val filter: (List<InetAddress>) -> List<InetAddress> = { addresses ->
        addresses.filter { LocalAddressPolicy.accepts(it, emptyList()) }
    },
) {
    fun approve(addresses: List<InetAddress>): List<InetAddress> = filter(addresses).takeIf { it.isNotEmpty() }
        ?: throw DeviceFailure(FailureKind.NETWORK, "No local device address on the selected LAN")
}
