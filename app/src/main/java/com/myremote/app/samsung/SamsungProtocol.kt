package com.myremote.app.samsung

import java.io.EOFException
import java.io.InputStream

/** Independently implemented interoperability subset. See docs/SAMSUNG_M360_PROTOCOL.md. */
internal object SamsungProtocol {
    const val SERVICE_UUID = "00001101-0000-1000-8000-00805f9b34fb"
    fun start(): ByteArray = frame(8, 1, 1)
    fun stop(): ByteArray = frame(8, 1, 0)
    fun volumeUp(): ByteArray = frame(11, 127, 1, 1)
    fun volumeDown(): ByteArray = frame(11, 127, 1, 0)
    fun mute(): ByteArray = frame(11, 116, 0)
    fun volumeQuery(): ByteArray = frame(11, 127, 0)
    fun muteQuery(): ByteArray = frame(11, 116, 16, 0)

    private fun frame(family: Int, command: Int, vararg parameters: Int): ByteArray =
        byteArrayOf(0xff.toByte(), family.toByte(), (1 + parameters.size).toByte(), command.toByte()) +
            parameters.map(Int::toByte).toByteArray()

    data class Frame(val family: Int, val command: Int, val parameters: ByteArray)

    /** RFCOMM is a stream: fragmented messages and multiple replies per read are both valid. */
    fun read(input: InputStream): Frame? {
        var skipped = 0
        while (true) {
            val next = input.read()
            if (next < 0) return null
            if (next == 255) break
            if (++skipped > 1024) throw java.io.IOException("Samsung framing lost")
        }
        fun octet(): Int = input.read().also { if (it < 0) throw EOFException("Samsung frame truncated") }
        val family = octet()
        val length = octet()
        if (length == 0) throw java.io.IOException("Samsung frame has no command")
        val command = octet()
        val parameters = ByteArray(length - 1) { octet().toByte() }
        return Frame(family, command, parameters)
    }

    fun volume(frame: Frame): Pair<Int, Int>? {
        if (frame.family != 11 || frame.command != 127 || frame.parameters.size < 3) return null
        val level = frame.parameters[1].toInt() and 255
        val maximum = frame.parameters[2].toInt() and 255
        return (level to maximum).takeIf { maximum > 0 && level <= maximum }
    }

    fun muted(frame: Frame): Boolean? =
        if (frame.family == 11 && frame.command == 116 && frame.parameters.size >= 2 &&
            frame.parameters[1].toInt() in 0..1) frame.parameters[1].toInt() == 1 else null
}
