package com.myremote.app.google

import android.annotation.SuppressLint
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Pairing trusts only a self-signed RSA certificate, then the displayed code authenticates it. */
@SuppressLint("CustomX509TrustManager")
internal class GoogleTvTrustManager(private val expectedPin: String?) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val cert = chain?.singleOrNull() ?: throw CertificateException("Expected one TV certificate")
        cert.checkValidity()
        if (cert.publicKey.algorithm != "RSA") throw CertificateException("Expected RSA TV certificate")
        if (expectedPin == null) cert.verify(cert.publicKey)
        else if (AndroidClientIdentity.certificatePin(cert) != expectedPin)
            throw CertificateException("TV certificate changed; pair again")
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
