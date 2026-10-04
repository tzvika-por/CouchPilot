package com.myremote.app.lg

import android.content.Context
import android.content.SharedPreferences

data class LgDevice(
    val name: String,
    val host: String,
    val model: String? = null,
    val uuid: String? = null,
    val wakeMacs: List<String> = emptyList(),
)

internal data class LgSavedDevice(
    val device: LgDevice, val clientKey: String?, val certificatePin: String?,
    val authorizationNeedsRefresh: Boolean = false,
)

internal fun normalizedLgUuid(value: String?): String? = value?.trim()?.lowercase()
    ?.removePrefix("uuid:")?.takeIf(String::isNotBlank)

/** App-private, backup-excluded metadata; the LG grant is encrypted using Android Keystore. */
internal class LgPairingStore(private val prefs: SharedPreferences, private val cipher: LgCredentialCipher) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("lg_webos_pairing", Context.MODE_PRIVATE), LgCredentialCipher.android(),
    )

    /** Startup status needs no decryption, key generation or synchronous migration. */
    @Synchronized fun configurationState(): com.myremote.app.domain.ConnectionState = when {
        prefs.getBoolean("authorization_refresh_required", false) -> com.myremote.app.domain.ConnectionState.AUTHORIZATION_REQUIRED
        prefs.getString("host", null) == null ||
            (!prefs.contains("client_key_encrypted") && !prefs.contains("client_key")) -> com.myremote.app.domain.ConnectionState.NOT_CONFIGURED
        else -> com.myremote.app.domain.ConnectionState.DISCONNECTED
    }

    @Synchronized fun read(): LgSavedDevice? {
        val host = prefs.getString("host", null) ?: return null
        val name = prefs.getString("name", host) ?: host
        val macs = prefs.getString("wake_macs", "").orEmpty().split(',').filter(String::isNotBlank)
        var credentialUnavailable = false
        val key = try {
            prefs.getString("client_key_encrypted", null)?.let(cipher::decrypt)
                ?: prefs.getString("client_key", null)?.also { legacy ->
                    val encrypted = cipher.encrypt(legacy)
                    check(prefs.edit().putString("client_key_encrypted", encrypted).remove("client_key").commit()) {
                        "Could not migrate LG credential"
                    }
                }
        } catch (_: java.security.GeneralSecurityException) {
            credentialUnavailable = true
            null
        } catch (_: IllegalArgumentException) {
            credentialUnavailable = true
            null
        } catch (_: java.security.ProviderException) {
            credentialUnavailable = true
            null
        }
        val device = LgDevice(name, host, prefs.getString("model", null), prefs.getString("uuid", null), macs)
        return LgSavedDevice(device,
            key, prefs.getString("certificate_pin", null),
            credentialUnavailable || prefs.getBoolean("authorization_refresh_required", false))
    }

    @Synchronized fun configureWakeAddress(address: String) {
        val saved = read() ?: error("Select the TV first")
        LgWakeOnLan.packet(address) // Validate before modifying the existing grant or metadata.
        selectOrUpdate(saved.device.copy(wakeMacs = listOf(address.uppercase().replace('-', ':'))))
    }

    private fun deviceEditor(device: LgDevice): SharedPreferences.Editor = prefs.edit()
        .putString("host", device.host).putString("name", device.name)
        .putString("model", device.model).putString("uuid", device.uuid)
        .putString("wake_macs", device.wakeMacs.joinToString(","))

    @Synchronized fun select(device: LgDevice) {
        check(deviceEditor(device)
            .remove("client_key").remove("client_key_encrypted").remove("certificate_pin").remove("authorization_revision")
            .remove("authorization_refresh_required").commit()) { "Could not save LG device" }
    }

    /** Refresh endpoint metadata without discarding an existing grant for the same TV. */
    @Synchronized fun selectOrUpdate(device: LgDevice) {
        val saved = read()
        val existing = saved?.device
        val previousUuid = normalizedLgUuid(existing?.uuid)
        val incomingUuid = normalizedLgUuid(device.uuid)
        val conflictingIdentity = previousUuid != null && incomingUuid != null && previousUuid != incomingUuid
        val sameIdentity = previousUuid != null && previousUuid == incomingUuid
        val sameHost = existing?.host?.equals(device.host, ignoreCase = true) == true
        if (sameHost && conflictingIdentity && saved.certificatePin != null) {
            // Unauthenticated discovery must never discard an existing endpoint's trust anchor.
            throw com.myremote.app.domain.DeviceFailure(com.myremote.app.domain.FailureKind.SECURITY,
                "LG identity changed; forget the old device before trusting a replacement")
        }
        if (existing == null || conflictingIdentity || (!sameHost && !sameIdentity)) {
            select(device)
            return
        }
        val merged = device.copy(
            model = device.model ?: existing.model,
            uuid = device.uuid ?: existing.uuid,
            wakeMacs = device.wakeMacs.ifEmpty { existing.wakeMacs },
        )
        check(deviceEditor(merged).commit()) { "Could not update LG device" }
    }

    /** Called only after registration on the TV's pinned TLS connection; never clears its grant. */
    @Synchronized fun learnedIdentity(uuid: String, certificatePin: String) {
        val saved = read() ?: return
        check(saved.certificatePin == certificatePin) { "LG identity belongs to a different connection" }
        val known = normalizedLgUuid(saved.device.uuid)
        check(known == null || known == normalizedLgUuid(uuid)) { "LG identity changed" }
        selectOrUpdate(saved.device.copy(uuid = uuid))
    }

    @Synchronized fun registered(clientKey: String, certificatePin: String) {
        require(clientKey.isNotBlank() && certificatePin.isNotBlank())
        val encrypted = cipher.encrypt(clientKey)
        check(prefs.edit().putString("client_key_encrypted", encrypted).remove("client_key")
            .putString("certificate_pin", certificatePin)
            .putInt("authorization_revision", LgProtocol.AUTHORIZATION_REVISION)
            .remove("authorization_refresh_required").commit()) { "Could not save LG pairing" }
    }

    /** Revoke only the local grant; retain device configuration and the already trusted TLS identity. */
    @Synchronized fun clearAuthorization(requireRefresh: Boolean = true) {
        check(prefs.edit().remove("client_key").remove("client_key_encrypted").remove("authorization_revision")
            .putBoolean("authorization_refresh_required", requireRefresh).commit()) {
            "Could not reset LG authorization"
        }
    }

    @Synchronized fun clear() { check(prefs.edit().clear().commit()) { "Could not clear LG pairing" } }
}

fun normalizedLgHost(input: String): String? {
    val value = input.trim()
    val host = if (value.startsWith("[") && value.endsWith("]")) value.drop(1).dropLast(1) else value
    if (host.isEmpty() || host.length > 253) return null
    val allowed = if (':' in host) Regex("[A-Za-z0-9:.%_-]+") else Regex("[A-Za-z0-9._-]+")
    return host.takeIf(allowed::matches)
}
