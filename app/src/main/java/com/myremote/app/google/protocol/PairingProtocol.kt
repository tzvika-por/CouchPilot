package com.myremote.app.google.protocol

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

internal object PairingProtocol {
    const val VERSION = 2
    const val OK = 200
    const val REQUEST = 10
    const val REQUEST_ACK = 11
    const val OPTIONS = 20
    const val CONFIGURATION = 30
    const val CONFIGURATION_ACK = 31
    const val SECRET = 40
    const val SECRET_ACK = 41

    fun wrap(type: Int, body: ByteArray): ByteArray = ProtoWire.concat(
        ProtoWire.integer(1, VERSION), ProtoWire.integer(2, OK), ProtoWire.message(type, body),
    )

    fun request(clientName: String): ByteArray = wrap(REQUEST, ProtoWire.concat(
        ProtoWire.string(1, "atvremote"), ProtoWire.string(2, clientName),
    ))

    fun options(): ByteArray = wrap(OPTIONS, ProtoWire.concat(
        ProtoWire.message(1, ProtoWire.concat(ProtoWire.integer(1, 3), ProtoWire.integer(2, 6))),
        ProtoWire.integer(3, 1),
    ))

    fun configuration(): ByteArray = wrap(CONFIGURATION, ProtoWire.concat(
        ProtoWire.message(1, ProtoWire.concat(ProtoWire.integer(1, 3), ProtoWire.integer(2, 6))),
        ProtoWire.integer(2, 1),
    ))

    fun secret(hash: ByteArray): ByteArray = wrap(SECRET, ProtoWire.bytes(1, hash))

    fun inspect(payload: ByteArray): PairingMessage {
        val fields = ProtoWire.fields(payload)
        require(fields.number(1) == VERSION.toLong()) { "Unsupported pairing protocol" }
        require(fields.number(2) == OK.toLong()) { "Pairing rejected by device" }
        val field = fields.firstOrNull { it.number in setOf(REQUEST_ACK, OPTIONS, CONFIGURATION_ACK, SECRET_ACK) && it.bytes != null }
            ?: error("Unexpected pairing response")
        return PairingMessage(field.number, field.bytes!!)
    }

    fun checkOptions(body: ByteArray) {
        val fields = ProtoWire.fields(body)
        require(fields.filter { it.number == 1 || it.number == 2 }.any {
            val encoding = ProtoWire.fields(it.bytes ?: return@any false)
            encoding.number(1) == 3L && encoding.number(2) == 6L
        }) { "TV does not offer six-character hexadecimal pairing" }
    }

    /** Polo authenticates the TLS certificates through the code displayed on the TV. */
    fun secretHash(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray {
        require(Regex("[0-9A-Fa-f]{6}").matches(code)) { "Enter the six-character TV code" }
        val pin = code.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(client, server).forEach { key ->
            digest.update(unsigned(key.modulus))
            digest.update(unsigned(key.publicExponent))
        }
        digest.update(pin, 2, 1)
        return digest.digest().also {
            require(it[0] == pin[0]) { "Pairing code does not match the TV" }
        }
    }

    private fun unsigned(number: BigInteger): ByteArray = number.toByteArray().let {
        if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
    }
}

internal data class PairingMessage(val type: Int, val body: ByteArray)

internal class PairingHandshake {
    enum class Step { NEW, REQUESTED, OPTIONS_SENT, CONFIGURATION_SENT, CODE_REQUIRED, SECRET_SENT, COMPLETE }
    var step = Step.NEW
        private set

    fun request(clientName: String): ByteArray {
        check(step == Step.NEW)
        step = Step.REQUESTED
        return PairingProtocol.request(clientName)
    }

    fun accept(payload: ByteArray): ByteArray? {
        val message = PairingProtocol.inspect(payload)
        return when (step) {
            Step.REQUESTED -> {
                check(message.type == PairingProtocol.REQUEST_ACK)
                step = Step.OPTIONS_SENT
                PairingProtocol.options()
            }
            Step.OPTIONS_SENT -> {
                check(message.type == PairingProtocol.OPTIONS)
                PairingProtocol.checkOptions(message.body)
                step = Step.CONFIGURATION_SENT
                PairingProtocol.configuration()
            }
            Step.CONFIGURATION_SENT -> {
                check(message.type == PairingProtocol.CONFIGURATION_ACK)
                step = Step.CODE_REQUIRED
                null
            }
            Step.SECRET_SENT -> {
                check(message.type == PairingProtocol.SECRET_ACK)
                step = Step.COMPLETE
                null
            }
            else -> error("Unexpected pairing step")
        }
    }

    fun submitSecret(hash: ByteArray): ByteArray {
        check(step == Step.CODE_REQUIRED)
        step = Step.SECRET_SENT
        return PairingProtocol.secret(hash)
    }
}
