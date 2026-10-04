package com.myremote.app.lg

/** Deterministic test key only; production uses a non-exportable Android Keystore key. */
internal fun testLgCipher() = LgCredentialCipher { javax.crypto.spec.SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") }
