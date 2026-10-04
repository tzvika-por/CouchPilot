package com.myremote.app.lg

import com.myremote.app.network.LanNetwork
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LgNetworkTest {
    @Test fun actualUdpReceiverGetsThreeWakePacketsForOnlyConfiguredMac() = runBlocking {
        val address = InetAddress.getByName("127.0.0.1")
        DatagramSocket(0, address).use { receiver ->
            receiver.soTimeout = 2_000
            val mac = "02:00:00:00:00:01"
            LgWakeOnLan.sendPackets(listOf(mac), listOf(address), receiver.localPort)
            repeat(3) {
                val reply = DatagramPacket(ByteArray(200), 200)
                receiver.receive(reply)
                assertEquals(102, reply.length)
                assertArrayEquals(LgWakeOnLan.packet(mac), reply.data.copyOf(reply.length))
            }
        }
    }
    @Test fun broadcastUsesPrefixOfSelectedLan() {
        assertEquals("192.0.2.255", LanNetwork.broadcast(InetAddress.getByName("192.0.2.7") as Inet4Address, 24).hostAddress)
        assertEquals("10.7.255.255", LanNetwork.broadcast(InetAddress.getByName("10.7.8.9") as Inet4Address, 16).hostAddress)
    }
    @Test fun householdMacsAreNeverAttachedToAnUnrelatedTv() {
        assertTrue(LgDevice("Other LG", "192.0.2.8", model = "55UK6700YVD", uuid = "other").wakeMacs.isEmpty())
        val configured = LgDevice("LG", "192.0.2.8", wakeMacs = listOf("02:00:00:00:00:01"))
        assertEquals(configured, configured)
    }
}
