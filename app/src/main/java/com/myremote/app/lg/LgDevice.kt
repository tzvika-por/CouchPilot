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

/** Installation-specific configuration, kept outside reusable protocol and Wake-on-LAN code. */
internal object LgInstallation {
    private val targetWakeMacs = listOf("02:00:00:00:00:03", "02:00:00:00:00:01")

    // The recorded UUID identifies this installation; model alone does not identify an individual TV.
    private const val targetUuid = "00000000-0000-4000-8000-000000000001"
    fun forSelectedDevice(device: LgDevice): LgDevice = device.copy(
        wakeMacs = device.wakeMacs.ifEmpty {
            if (normalizedLgUuid(device.uuid) == targetUuid) targetWakeMacs else emptyList()
        },
    )
}

internal fun normalizedLgUuid(value: String?): String? = value?.trim()?.lowercase()
    ?.removePrefix("uuid:")?.takeIf(String::isNotBlank)

/** App-private preferences are excluded from Android backup by the application manifest. */
internal class LgPairingStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("lg_webos_pairing", Context.MODE_PRIVATE),
    )

    fun read(): LgSavedDevice? {
        val host = prefs.getString("host", null) ?: return null
        val name = prefs.getString("name", host) ?: host
        val macs = prefs.getString("wake_macs", "").orEmpty().split(',').filter(String::isNotBlank)
        val key = prefs.getString("client_key", null)
        val device = LgDevice(name, host, prefs.getString("model", null), prefs.getString("uuid", null), macs)
        val migrated = LgInstallation.forSelectedDevice(device)
        if (migrated.wakeMacs != macs) {
            check(prefs.edit().putString("wake_macs", migrated.wakeMacs.joinToString(",")).commit()) {
                "Could not save LG wake configuration"
            }
        }
        return LgSavedDevice(migrated,
            key, prefs.getString("certificate_pin", null),
            prefs.getBoolean("authorization_refresh_required", false))
    }

    private fun deviceEditor(device: LgDevice): SharedPreferences.Editor = prefs.edit()
        .putString("host", device.host).putString("name", device.name)
        .putString("model", device.model).putString("uuid", device.uuid)
        .putString("wake_macs", device.wakeMacs.joinToString(","))

    fun select(device: LgDevice) {
        check(deviceEditor(LgInstallation.forSelectedDevice(device))
            .remove("client_key").remove("certificate_pin").remove("authorization_revision")
            .remove("authorization_refresh_required").commit()) { "Could not save LG device" }
    }

    /** Refresh endpoint metadata without discarding an existing grant for the same TV. */
    fun selectOrUpdate(device: LgDevice) {
        val existing = read()?.device
        val previousUuid = normalizedLgUuid(existing?.uuid)
        val incomingUuid = normalizedLgUuid(device.uuid)
        val conflictingIdentity = previousUuid != null && incomingUuid != null && previousUuid != incomingUuid
        val sameIdentity = previousUuid != null && previousUuid == incomingUuid
        val sameHost = existing?.host?.equals(device.host, ignoreCase = true) == true
        if (existing == null || conflictingIdentity || (!sameHost && !sameIdentity)) {
            select(device)
            return
        }
        val merged = LgInstallation.forSelectedDevice(device.copy(
            model = device.model ?: existing.model,
            uuid = device.uuid ?: existing.uuid,
            wakeMacs = device.wakeMacs.ifEmpty { existing.wakeMacs },
        ))
        check(deviceEditor(merged).commit()) { "Could not update LG device" }
    }

    /** Called only after registration on the TV's pinned TLS connection; never clears its grant. */
    fun learnedIdentity(uuid: String, certificatePin: String) {
        val saved = read() ?: return
        check(saved.certificatePin == certificatePin) { "LG identity belongs to a different connection" }
        val known = normalizedLgUuid(saved.device.uuid)
        check(known == null || known == normalizedLgUuid(uuid)) { "LG identity changed" }
        selectOrUpdate(saved.device.copy(uuid = uuid))
    }

    fun registered(clientKey: String, certificatePin: String) {
        require(clientKey.isNotBlank() && certificatePin.isNotBlank())
        check(prefs.edit().putString("client_key", clientKey)
            .putString("certificate_pin", certificatePin)
            .putInt("authorization_revision", LgProtocol.AUTHORIZATION_REVISION)
            .remove("authorization_refresh_required").commit()) { "Could not save LG pairing" }
    }

    /** Revoke only the local grant; retain device configuration and the already trusted TLS identity. */
    fun clearAuthorization(requireRefresh: Boolean = true) {
        check(prefs.edit().remove("client_key").remove("authorization_revision")
            .putBoolean("authorization_refresh_required", requireRefresh).commit()) {
            "Could not reset LG authorization"
        }
    }

    fun clear() { check(prefs.edit().clear().commit()) { "Could not clear LG pairing" } }
}

fun normalizedLgHost(input: String): String? {
    val value = input.trim()
    val host = if (value.startsWith("[") && value.endsWith("]")) value.drop(1).dropLast(1) else value
    if (host.isEmpty() || host.length > 253) return null
    val allowed = if (':' in host) Regex("[A-Za-z0-9:.%_-]+") else Regex("[A-Za-z0-9._-]+")
    return host.takeIf(allowed::matches)
}
