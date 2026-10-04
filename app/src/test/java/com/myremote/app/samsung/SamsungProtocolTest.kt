package com.myremote.app.samsung

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class SamsungProtocolTest {
    // Independent byte vectors transcribed from verified vendor command definitions (no vendor code).
    @Test fun commandVectorsMatchVerifiedVendorWireDefinitions() {
        fun assertHex(hex: String, bytes: ByteArray) = assertEquals(hex, bytes.joinToString("") { "%02x".format(it.toInt() and 255) })
        assertHex("ff08020101", SamsungProtocol.start())
        assertHex("ff08020100", SamsungProtocol.stop())
        assertHex("ff0b037f0101", SamsungProtocol.volumeUp())
        assertHex("ff0b037f0100", SamsungProtocol.volumeDown())
        assertHex("ff0b027400", SamsungProtocol.mute())
        assertHex("ff0b027f00", SamsungProtocol.volumeQuery())
        assertHex("ff0b03741000", SamsungProtocol.muteQuery())
    }
    @Test fun readsFragmentedAndCoalescedRepliesWithoutLosingFrameBoundary() {
        val raw = byteArrayOf(0, 0xff.toByte(), 11, 4, 127, 0, 12, 50, 0xff.toByte(), 11, 3, 116, 0, 1)
        val input = object : InputStream() {
            val data = ByteArrayInputStream(raw)
            override fun read() = data.read()
            override fun read(bytes: ByteArray, offset: Int, length: Int) = data.read(bytes, offset, minOf(length, 1))
        }
        assertEquals(12 to 50, SamsungProtocol.volume(SamsungProtocol.read(input)!!))
        assertEquals(true, SamsungProtocol.muted(SamsungProtocol.read(input)!!))
        assertNull(SamsungProtocol.read(input))
    }
    @Test fun refusesTruncatedZeroLengthAndUnboundedNoise() {
        assertThrows(EOFException::class.java) { SamsungProtocol.read(byteArrayOf(0xff.toByte(), 11, 4, 127, 0).inputStream()) }
        assertThrows(java.io.IOException::class.java) { SamsungProtocol.read(byteArrayOf(0xff.toByte(), 11, 0).inputStream()) }
        assertThrows(java.io.IOException::class.java) { SamsungProtocol.read(ByteArray(1026).inputStream()) }
    }
    @Test fun validatesStatusRatherThanTreatingAnyBluetoothResponseAsSuccess() {
        assertNull(SamsungProtocol.volume(SamsungProtocol.Frame(8, 127, byteArrayOf(0, 12, 50))))
        assertNull(SamsungProtocol.volume(SamsungProtocol.Frame(11, 127, byteArrayOf(0, 51, 50))))
        assertNull(SamsungProtocol.muted(SamsungProtocol.Frame(11, 116, byteArrayOf(0, 2))))
    }
}
