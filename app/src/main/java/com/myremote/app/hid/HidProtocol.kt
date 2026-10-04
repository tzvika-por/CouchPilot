package com.myremote.app.hid

import com.myremote.app.domain.RemoteKey

data class HidReport(val id: Int, val bytes: ByteArray)

/** Independently encoded USB HID usages; API sendReport excludes the report ID byte. */
object HidProtocol {
    val descriptor: ByteArray get() = hex("""
        05 01 09 06 A1 01 85 01
        05 07 19 E0 29 E7 15 00 25 01 75 01 95 08 81 02
        75 08 95 01 81 03
        05 08 19 01 29 05 75 01 95 05 91 02 75 03 95 01 91 03
        05 07 19 00 2A FF 00 15 00 26 FF 00 75 08 95 06 81 00 C0
        05 0C 09 01 A1 01 85 02
        19 00 2A FF 03 15 00 26 FF 03 75 10 95 01 81 00 C0
        05 01 09 80 A1 01 85 03
        19 00 29 83 15 00 26 83 00 75 08 95 01 81 00 C0
    """)

    fun key(key: RemoteKey): HidReport {
        val digit = when (key) {
            RemoteKey.DIGIT_0 -> 0
            RemoteKey.DIGIT_1 -> 1
            RemoteKey.DIGIT_2 -> 2
            RemoteKey.DIGIT_3 -> 3
            RemoteKey.DIGIT_4 -> 4
            RemoteKey.DIGIT_5 -> 5
            RemoteKey.DIGIT_6 -> 6
            RemoteKey.DIGIT_7 -> 7
            RemoteKey.DIGIT_8 -> 8
            RemoteKey.DIGIT_9 -> 9
            else -> null
        }
        if (digit != null) return HidReport(1, byteArrayOf(0, 0, (if (digit == 0) 0x27 else 0x1d + digit).toByte(), 0, 0, 0, 0, 0))
        val usage = when (key) {
            RemoteKey.UP -> 0x42
            RemoteKey.DOWN -> 0x43
            RemoteKey.LEFT -> 0x44
            RemoteKey.RIGHT -> 0x45
            RemoteKey.CENTER -> 0x41 // Menu Pick -> Linux KEY_SELECT -> Android DPAD_CENTER.
            RemoteKey.BACK -> 0x224
            RemoteKey.HOME -> 0x223
            RemoteKey.PLAY_PAUSE -> 0xcd
            RemoteKey.REWIND -> 0xb4
            RemoteKey.FAST_FORWARD -> 0xb3
            RemoteKey.CHANNEL_UP -> 0x9c
            RemoteKey.CHANNEL_DOWN -> 0x9d
            else -> error("Unsupported key")
        }
        return HidReport(2, byteArrayOf(usage.toByte(), (usage shr 8).toByte()))
    }

    fun power(wake: Boolean) = HidReport(3, byteArrayOf(if (wake) 0x83.toByte() else 0x82.toByte()))
    fun released(id: Int): HidReport? = when (id) {
        1 -> HidReport(id, ByteArray(8))
        2 -> HidReport(id, ByteArray(2))
        3 -> HidReport(id, ByteArray(1))
        else -> null
    }
    private fun hex(value: String) = value.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
}
