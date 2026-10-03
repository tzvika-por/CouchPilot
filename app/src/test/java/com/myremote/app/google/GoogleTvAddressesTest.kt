package com.myremote.app.google

import java.net.ConnectException
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleTvAddressesTest {
    private val ipv4 = InetAddress.getByName("192.0.2.8")
    private val ipv6 = InetAddress.getByName("2001:db8::8")

    @Test fun unreachableIpv4FallsBackToDiscoveredIpv6() {
        val addresses = GoogleTvAddresses.candidates("tv.local", listOf(ipv4, ipv6), null) { emptyList() }
        val attempted = mutableListOf<InetAddress>()
        val selected = GoogleTvAddresses.firstConnected(addresses) { address ->
            attempted += address
            if (address == ipv4) throw ConnectException("IPv4 unreachable")
            address
        }
        assertEquals(ipv6, selected)
        assertEquals(listOf(ipv4, ipv6), attempted)
    }

    @Test fun lastUsableAddressIsTriedFirstAndDuplicatesAreRemoved() {
        val addresses = GoogleTvAddresses.candidates(
            "tv.local", listOf(ipv4, ipv6), ipv6.hostAddress,
        ) { listOf(ipv4, ipv6) }
        assertEquals(listOf(ipv6, ipv4), addresses)
    }

    @Test fun discoveredIpv6SurvivesHostnameLookupFailure() {
        val addresses = GoogleTvAddresses.candidates("tv.local", listOf(ipv6), null) {
            throw UnknownHostException("tv.local")
        }
        assertEquals(listOf(ipv6), addresses)
        assertThrows(UnknownHostException::class.java) {
            GoogleTvAddresses.candidates("tv.local", emptyList(), null) { throw UnknownHostException("tv.local") }
        }
    }

    @Test fun ipv6OnlyHostnameResolutionIsUsable() {
        val addresses = GoogleTvAddresses.candidates("tv.local", emptyList(), null) { listOf(ipv6) }
        assertEquals(ipv6, GoogleTvAddresses.firstConnected(addresses) { it })
    }

    @Test fun manualHostAcceptsDnsAndIpv6LiteralForms() {
        assertEquals("Android_a0fa.local", normalizedGoogleTvHost(" Android_a0fa.local "))
        assertEquals("2001:db8::8", normalizedGoogleTvHost("[2001:db8::8]"))
        assertEquals("fe80::1%wlan0", normalizedGoogleTvHost("fe80::1%wlan0"))
        assertEquals(null, normalizedGoogleTvHost("[2001:db8::8]:6467"))
    }
}
