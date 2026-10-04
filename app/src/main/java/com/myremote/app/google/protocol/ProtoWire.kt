package com.myremote.app.google.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** The small subset of protobuf wire types used by Android TV Remote Service v2. */
internal object ProtoWire {
    const val MAX_FRAME = 64 * 1024

    fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var remaining = value
        do {
            val part = (remaining and 0x7f).toInt()
            remaining = remaining ushr 7
            out.write(if (remaining == 0L) part else part or 0x80)
        } while (remaining != 0L)
        return out.toByteArray()
    }

    fun integer(number: Int, value: Int): ByteArray = concat(varint((number * 8).toLong()), varint(value.toLong()))
    fun bytes(number: Int, value: ByteArray): ByteArray = concat(varint((number * 8 + 2).toLong()), varint(value.size.toLong()), value)
    fun string(number: Int, value: String): ByteArray = bytes(number, value.toByteArray(Charsets.UTF_8))
    fun message(number: Int, value: ByteArray): ByteArray = bytes(number, value)
    fun concat(vararg parts: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> parts.forEach(out::write) }.toByteArray()

    fun fields(data: ByteArray): List<Field> {
        val input = data.inputStream()
        val result = mutableListOf<Field>()
        while (input.available() > 0) {
            val tag = readVarint(input) ?: break
            val number = (tag ushr 3).toInt()
            require(number > 0) { "Invalid protobuf field" }
            when ((tag and 7).toInt()) {
                0 -> result += Field(number, readVarint(input) ?: throw EOFException(), null)
                2 -> {
                    val length = readVarint(input) ?: throw EOFException()
                    require(length in 0..MAX_FRAME.toLong()) { "Invalid protobuf length" }
                    result += Field(number, null, readExact(input, length.toInt()))
                }
                1 -> readExact(input, 8)
                5 -> readExact(input, 4)
                else -> error("Unsupported protobuf wire type")
            }
        }
        return result
    }

    fun frame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_FRAME)
        return concat(varint(payload.size.toLong()), payload)
    }

    fun readFrame(input: InputStream): ByteArray? {
        val length = readVarint(input) ?: return null
        require(length in 0..MAX_FRAME.toLong()) { "Invalid frame length" }
        return readExact(input, length.toInt())
    }

    fun writeFrame(output: OutputStream, payload: ByteArray) {
        output.write(frame(payload))
        output.flush()
    }

    private fun readVarint(input: InputStream): Long? {
        var value = 0L
        for (shift in 0 until 70 step 7) {
            val next = input.read()
            if (next < 0) {
                if (shift == 0) return null
                throw EOFException("Truncated varint")
            }
            require(shift != 63 || next and 0x7e == 0) { "Varint overflow" }
            value = value or ((next and 0x7f).toLong() shl shift)
            if (next and 0x80 == 0) return value
        }
        error("Varint too long")
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(result, offset, size - offset)
            if (count < 0) throw EOFException("Truncated frame")
            offset += count
        }
        return result
    }
}

internal data class Field(val number: Int, val integer: Long?, val bytes: ByteArray?)
internal fun List<Field>.number(id: Int): Long? = firstOrNull { it.number == id }?.integer
internal fun List<Field>.data(id: Int): ByteArray? = firstOrNull { it.number == id }?.bytes
