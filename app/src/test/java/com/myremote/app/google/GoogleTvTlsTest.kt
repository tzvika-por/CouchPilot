package com.myremote.app.google

import java.net.InetAddress
import java.security.cert.CertificateException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class GoogleTvTlsTest {
    @Test fun pairingChecksRsaSelfSignatureAndSavedPinRejectsIdentityChange() {
        val certificate = HeldCertificate.Builder().rsa2048().commonName("TV").build().certificate
        GoogleTvTrustManager(null).checkServerTrusted(arrayOf(certificate), "RSA")
        GoogleTvTrustManager(AndroidClientIdentity.certificatePin(certificate)).checkServerTrusted(arrayOf(certificate), "RSA")
        assertThrows(CertificateException::class.java) { GoogleTvTrustManager("00".repeat(32)).checkServerTrusted(arrayOf(certificate), "RSA") }
        assertThrows(CertificateException::class.java) { GoogleTvTrustManager(null).checkServerTrusted(emptyArray(), "RSA") }
        val ec = HeldCertificate.Builder().commonName("EC").build().certificate
        assertThrows(CertificateException::class.java) { GoogleTvTrustManager(null).checkServerTrusted(arrayOf(ec), "EC") }
    }
    @Test fun actualIpv6TlsHandshakeUsesProductionTrustPolicy() {
        val certificate = HeldCertificate.Builder().rsa2048().commonName("Simulated Google TV").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val address = InetAddress.getByName("::1")
        val server = serverTls.sslContext().serverSocketFactory.createServerSocket(0, 1, address) as SSLServerSocket
        server.soTimeout = 3_000
        val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val thread = Thread {
            try { (server.accept() as SSLSocket).use { socket ->
                socket.soTimeout = 3_000
                socket.startHandshake()
                assertEquals(42, socket.inputStream.read())
                socket.outputStream.write(43)
            } } catch (failure: Throwable) { error.set(failure) }
        }
        thread.start()
        try {
            val context = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(GoogleTvTrustManager(AndroidClientIdentity.certificatePin(certificate.certificate))), null)
            }
            (context.socketFactory.createSocket(address, server.localPort) as SSLSocket).use { socket ->
                socket.soTimeout = 3_000; socket.startHandshake()
                socket.outputStream.write(42); assertEquals(43, socket.inputStream.read())
            }
        } finally { server.close(); thread.join(4_000) }
        assertFalse(thread.isAlive)
        error.get()?.let { throw AssertionError("Local TLS server failed", it) }
    }
}
