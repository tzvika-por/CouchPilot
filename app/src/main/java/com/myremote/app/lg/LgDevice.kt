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

internal data class LgSavedDevice(val device: LgDevice, val clientKey: String?, val certificatePin: String?)

/** Installation-specific configuration, kept outside reusable protocol and Wake-on-LAN code. */
internal object LgInstallation {
    private val targetWakeMacs = listOf("02:00:00:00:00:03", "02:00:00:00:00:01")

    fun forSelectedDevice(device: LgDevice): LgDevice = device.copy(
        wakeMacs = device.wakeMacs.ifEmpty { targetWakeMacs },
    )
}

/** App-private preferences are excluded from Android backup by the application manifest. */
internal class LgPairingStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("lg_webos_pairing", Context.MODE_PRIVATE),
    )

    fun read(): LgSavedDevice? {
        val host = prefs.getString("host", null) ?: return null
        val name = prefs.getString("name", host) ?: host
        val macs = prefs.getString("wake_macs", "").orEmpty().split(',').filter(String::isNotBlank)
        return LgSavedDevice(LgDevice(name, host, prefs.getString("model", null),
            prefs.getString("uuid", null), macs),
            prefs.getString("client_key", null), prefs.getString("certificate_pin", null))
    }

    fun select(device: LgDevice) {
        check(prefs.edit().putString("host", device.host).putString("name", device.name)
            .putString("model", device.model).putString("uuid", device.uuid)
            .putString("wake_macs", device.wakeMacs.joinToString(","))
            .remove("client_key").remove("certificate_pin").commit()) { "Could not save LG device" }
    }

    fun registered(clientKey: String, certificatePin: String) {
        require(clientKey.isNotBlank() && certificatePin.isNotBlank())
        check(prefs.edit().putString("client_key", clientKey)
            .putString("certificate_pin", certificatePin).commit()) { "Could not save LG pairing" }
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
