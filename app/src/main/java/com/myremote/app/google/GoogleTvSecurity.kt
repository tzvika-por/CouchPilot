package com.myremote.app.google

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/** Only endpoint details and the public certificate pin are in preferences. Private key stays in Android Keystore. */
internal class PairingStore(private val prefs: android.content.SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("google_tv_pairing", Context.MODE_PRIVATE))

    fun saved(): SavedPairing? {
        val host = prefs.getString("host", null) ?: return null
        val pin = prefs.getString("pin", null) ?: return null
        return SavedPairing(GoogleTvDevice(
            prefs.getString("name", host) ?: host, host, prefs.getInt("port", 6466),
            resolvedAddresses = prefs.getString("addresses", "").orEmpty().split(',').filter(String::isNotBlank)
                .mapNotNull { address -> runCatching { java.net.InetAddress.getByName(address) }.getOrNull() },
            lastSuccessfulAddress = prefs.getString("last_address", null),
        ), pin)
    }

    fun save(device: GoogleTvDevice, pin: String, connectedAddress: String) {
        check(prefs.edit().putString("host", device.host).putString("name", device.name)
            .putInt("port", device.port).putString("pin", pin)
            .putString("addresses", device.resolvedAddresses.mapNotNull { it.hostAddress }.joinToString(","))
            .putString("last_address", connectedAddress).commit()) { "Could not save pairing" }
    }

    fun rememberAddress(address: String) {
        prefs.edit().putString("last_address", address).apply()
    }

    fun clear() { check(prefs.edit().clear().commit()) { "Could not clear pairing" } }
}

internal data class SavedPairing(val device: GoogleTvDevice, val serverPin: String)

internal class AndroidClientIdentity {
    private val alias = "myremote-google-tv-client"
    private val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val certificate: X509Certificate by lazy {
        if (!store.containsAlias(alias)) {
            val now = System.currentTimeMillis()
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=My Remote"))
                .setCertificateSerialNumber(java.math.BigInteger.valueOf(now))
                .setCertificateNotBefore(Date(now - 86_400_000L))
                .setCertificateNotAfter(Date(now + 20L * 365 * 86_400_000L))
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                .apply { initialize(spec) }.generateKeyPair()
        }
        store.getCertificate(alias) as X509Certificate
    }

    private val keyManager: KeyManager by lazy {
        certificate
        object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<java.security.Principal>?): Array<String> = arrayOf(alias)
            override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<java.security.Principal>?, socket: java.net.Socket?): String = alias
            override fun getServerAliases(keyType: String?, issuers: Array<java.security.Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<java.security.Principal>?, socket: java.net.Socket?): String? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(certificate)
            override fun getPrivateKey(alias: String?): PrivateKey = store.getKey(this@AndroidClientIdentity.alias, null) as PrivateKey
        }
    }

    // Polo uses a self-signed TV certificate: the displayed code authenticates it at pairing,
    // and every later connection requires the saved SHA-256 pin. This context is app-local.
    @SuppressLint("CustomX509TrustManager")
    fun context(expectedPin: String?): SSLContext {
        val trust = GoogleTvTrustManager(expectedPin)
        return SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf<TrustManager>(trust), null) }
    }

    companion object {
        fun certificatePin(cert: X509Certificate): String = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }
}

internal interface GoogleTvSocketFactory {
    suspend fun open(device: GoogleTvDevice, port: Int, serverPin: String?): SSLSocket
}

internal class AndroidGoogleTvSocketFactory(private val identity: AndroidClientIdentity, private val lan: com.myremote.app.network.LanNetwork) : GoogleTvSocketFactory {
    override suspend fun open(device: GoogleTvDevice, port: Int, serverPin: String?): SSLSocket = GoogleTvSocketIo.work { own ->
        val factory = identity.context(serverPin).socketFactory
        val network = lan.selected(device.network)
        val addresses = GoogleTvAddresses.candidates(
            device.host, device.resolvedAddresses, device.lastSuccessfulAddress,
            resolve = { host -> (network?.getAllByName(host) ?: java.net.InetAddress.getAllByName(host)).toList() },
        )
        val socket = GoogleTvAddresses.firstConnected(addresses) { address ->
            val candidate = factory.createSocket() as SSLSocket
            own(candidate)
            try {
                network?.bindSocket(candidate)
                candidate.soTimeout = 15_000
                candidate.connect(java.net.InetSocketAddress(address, port), 8_000)
                candidate
            } catch (error: Exception) {
                candidate.close()
                throw error
            }
        }
        try {
            // A certificate or handshake failure is not evidence that another address is safe.
            socket.startHandshake()
            socket
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }
}
