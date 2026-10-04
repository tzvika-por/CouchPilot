package com.myremote.app.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LocalAddressPolicyTest {
    private fun accepts(host: String) = LocalAddressPolicy.accepts(InetAddress.getByName(host), emptyList())
    @Test fun privateAndLinkLocalAddressesRemainAvailableInBothFamilies() {
        listOf("10.0.0.1", "172.16.1.2", "192.168.1.2", "169.254.1.2", "fd00::1", "fe80::1").forEach { assertTrue(it, accepts(it)) }
    }
    @Test fun loopbackUnspecifiedMulticastAndWanAreRejected() {
        listOf("127.0.0.1", "::1", "0.0.0.0", "::", "224.0.0.1", "ff02::1", "8.8.8.8", "2001:4860::1").forEach { assertFalse(it, accepts(it)) }
    }
    @Test fun globallyAddressedIpv6OnTheLanIsAcceptedWithoutAllowingOtherPrefixes() {
        val prefix = listOf(InetAddress.getByName("2001:db8:12::2") to 64)
        assertTrue(LocalAddressPolicy.accepts(InetAddress.getByName("2001:db8:12::8"), prefix))
        assertFalse(LocalAddressPolicy.accepts(InetAddress.getByName("2001:db8:13::8"), prefix))
        assertFalse(LocalAddressPolicy.accepts(InetAddress.getByName("8.8.8.8"), prefix))
    }
}
