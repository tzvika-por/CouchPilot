package com.myremote.app.hid

import android.content.Context

/** Only selection persists; Android owns Bluetooth bond keys. Backups are disabled app-wide. */
class HidStore(context: Context) {
    private val preferences = context.getSharedPreferences("streamer_connection", Context.MODE_PRIVATE)
    fun mode() = if (preferences.getString("mode", null) == "bluetooth") StreamerConnection.BLUETOOTH else StreamerConnection.LAN
    fun mode(value: StreamerConnection) { check(preferences.edit().putString("mode", value.name.lowercase()).commit()) }
    fun host(): HidHost? = preferences.getString("address", null)?.let { HidHost(preferences.getString("name", null) ?: "TV", it) }
    fun host(value: HidHost?) {
        check(preferences.edit().putString("name", value?.name).putString("address", value?.address).commit())
    }
}
