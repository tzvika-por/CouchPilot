package com.myremote.app.hid

import com.myremote.app.domain.RemoteKey
import org.junit.Assert.*
import org.junit.Test

class HidProtocolTest {
    @Test fun independentUsageVectorsCoverEveryCustomerKey() {
        val usages = mapOf(RemoteKey.UP to 0x42, RemoteKey.DOWN to 0x43, RemoteKey.LEFT to 0x44,
            RemoteKey.RIGHT to 0x45, RemoteKey.CENTER to 0x41, RemoteKey.BACK to 0x224,
            RemoteKey.HOME to 0x223, RemoteKey.PLAY_PAUSE to 0xcd, RemoteKey.REWIND to 0xb4,
            RemoteKey.FAST_FORWARD to 0xb3, RemoteKey.CHANNEL_UP to 0x9c, RemoteKey.CHANNEL_DOWN to 0x9d)
        usages.forEach { (key, usage) ->
            val report = HidProtocol.key(key)
            assertEquals(2, report.id)
            assertArrayEquals(byteArrayOf(usage.toByte(), (usage shr 8).toByte()), report.bytes)
        }
        (0..9).forEach { digit ->
            val report = HidProtocol.key(RemoteKey.valueOf("DIGIT_$digit"))
            assertEquals(1, report.id)
            assertEquals(8, report.bytes.size)
            assertEquals(if (digit == 0) 0x27 else 0x1e + digit - 1, report.bytes[2].toInt())
            assertEquals(0, report.bytes.filterIndexed { index, _ -> index != 2 }.sumOf { it.toInt() })
        }
        assertEquals(RemoteKey.entries.size, usages.size + 10)
        assertArrayEquals(byteArrayOf(0x83.toByte()), HidProtocol.power(true).bytes)
        assertArrayEquals(byteArrayOf(0x82.toByte()), HidProtocol.power(false).bytes)
        assertNull(HidProtocol.released(0))
    }

    @Test fun hostReportRequestsRetainPressedKeyAndHandleDeclaredLedOutput() {
        val reports = HidReports()
        assertArrayEquals(ByteArray(8), reports.get(1, 1, 0))
        reports.record(HidReport(2, byteArrayOf(0x41, 0)))
        assertArrayEquals(byteArrayOf(0x41, 0), reports.get(1, 2, 2))
        assertNull(reports.get(1, 2, 1))
        assertNull(reports.get(1, 4, 0))
        assertNull(reports.get(3, 1, 0))
        assertFalse(reports.set(1, 1, byteArrayOf(7)))
        assertTrue(reports.set(2, 1, byteArrayOf(7)))
        assertArrayEquals(byteArrayOf(7), reports.get(2, 1, 1))
        assertArrayEquals(ByteArray(8), reports.get(1, 1, 8))
        reports.clear()
        assertArrayEquals(byteArrayOf(0, 0), reports.get(1, 2, 0))
        assertArrayEquals(byteArrayOf(0), reports.get(2, 1, 0))
    }

    @Test fun descriptorDeclaresMatchingInputLengthsAndKeyboardLedOutput() {
        // Independent short-item parser verifies the actual descriptor supplied to Android.
        val data = HidProtocol.descriptor
        val inputBits = mutableMapOf<Int, Int>(); val outputBits = mutableMapOf<Int, Int>()
        var index = 0; var id = 0; var size = 0; var count = 0; var depth = 0
        while (index < data.size) {
            val prefix = data[index++].toInt() and 255
            assertNotEquals(0xfe, prefix)
            val length = if (prefix and 3 == 3) 4 else prefix and 3
            var value = 0
            repeat(length) { shift -> value = value or ((data[index++].toInt() and 255) shl (8 * shift)) }
            when (prefix and 0xfc) {
                0x84 -> id = value
                0x74 -> size = value
                0x94 -> count = value
                0x80 -> inputBits[id] = (inputBits[id] ?: 0) + size * count
                0x90 -> outputBits[id] = (outputBits[id] ?: 0) + size * count
                0xa0 -> depth++
                0xc0 -> depth--
            }
            assertTrue(depth >= 0)
        }
        assertEquals(0, depth)
        assertEquals(mapOf(1 to 64, 2 to 16, 3 to 8), inputBits)
        assertEquals(mapOf(1 to 8), outputBits)
        inputBits.forEach { (id, bits) -> assertEquals(bits / 8, requireNotNull(HidProtocol.released(id)).bytes.size) }
    }
}
