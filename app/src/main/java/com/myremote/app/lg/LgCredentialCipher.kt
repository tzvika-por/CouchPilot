package com.myremote.app.lg

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption; Android owns the non-exportable wrapping key. No plaintext fallback. */
internal class LgCredentialCipher(private val loadKey: () -> SecretKey) {
    private val key by lazy(loadKey)
    private val aad = "myremote.lg.client-key.v1".toByteArray(Charsets.UTF_8)

    fun encrypt(value: String): String {
        require(value.isNotBlank() && value.length <= 4_096)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        require(cipher.iv.size == 12)
        cipher.updateAAD(aad)
        val envelope = byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(envelope)
    }

    fun decrypt(value: String): String {
        require(value.length <= 24_000)
        val envelope = Base64.getDecoder().decode(value)
        if (envelope.size < 29 || envelope[0] != 1.toByte()) throw GeneralSecurityException("Invalid LG credential envelope")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(envelope.copyOfRange(13, envelope.size)).toString(Charsets.UTF_8).also {
            require(it.isNotBlank() && it.length <= 4_096)
        }
    }

    companion object {
        fun android() = LgCredentialCipher(::androidKey)

        @Synchronized private fun androidKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "myremote-lg-client-key-v1"
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build())
            }.generateKey()
        }
    }
}
