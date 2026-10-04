package com.myremote.app.lg

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LgPairingStoreTest {
    @Test fun clientKeyPinAndWakeConfigurationSurviveStoreRecreation() {
        val preferences = MemoryPreferences()
        val device = LgDevice("Living room", "192.0.2.8", "55UK6700YVD", "uuid:lg",
            listOf("02:00:00:00:00:03"))
        LgPairingStore(preferences).apply {
            select(device)
            assertNull(read()?.clientKey)
            registered("client-key", "certificate-pin")
        }
        val restored = LgPairingStore(preferences).read()!!
        assertEquals(device, restored.device)
        assertEquals("client-key", restored.clientKey)
        assertEquals("certificate-pin", restored.certificatePin)
        LgPairingStore(preferences).select(device.copy(host = "192.0.2.9"))
        assertNull(LgPairingStore(preferences).read()?.clientKey)
        LgPairingStore(preferences).clear()
        assertNull(LgPairingStore(preferences).read())
    }

    @Test fun authorizationResetKeepsDevicePinAndUnrelatedPreferences() {
        val prefs = MemoryPreferences()
        val device = LgInstallation.forSelectedDevice(LgDevice("LG", "192.0.2.8"))
        val store = LgPairingStore(prefs)
        store.select(device)
        store.registered("old-key", "trusted-pin")
        prefs.edit().putString("unrelated", "keep").commit()
        store.clearAuthorization()
        val invalidated = store.read()!!
        assertNull(invalidated.clientKey)
        assertEquals(true, invalidated.authorizationNeedsRefresh)
        assertEquals(device, invalidated.device)
        assertEquals("trusted-pin", invalidated.certificatePin)
        store.clearAuthorization(requireRefresh = false)
        assertEquals(false, store.read()!!.authorizationNeedsRefresh)
        assertEquals("keep", prefs.getString("unrelated", null))
        assertEquals(false, org.json.JSONObject(LgProtocol.register("refresh", store.read()!!.clientKey))
            .getJSONObject("payload").has("client-key"))
        store.registered("new-key", "trusted-pin")
        assertEquals("new-key", LgPairingStore(prefs).read()!!.clientKey)
        assertEquals(false, LgPairingStore(prefs).read()!!.authorizationNeedsRefresh)
    }

    @Test fun unversionedAndOutdatedGrantsCannotBeReused() {
        val prefs = MemoryPreferences()
        val store = LgPairingStore(prefs)
        store.select(LgDevice("LG", "192.0.2.8"))
        store.registered("old-key", "trusted-pin")
        prefs.edit().remove("authorization_revision").commit()
        assertNull(store.read()!!.clientKey)
        assertEquals(true, store.read()!!.authorizationNeedsRefresh)
        assertEquals("trusted-pin", store.read()!!.certificatePin)
        prefs.edit().putInt("authorization_revision", LgProtocol.AUTHORIZATION_REVISION - 1).commit()
        assertNull(store.read()!!.clientKey)
        assertEquals(true, store.read()!!.authorizationNeedsRefresh)
    }

    internal class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            values[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { updates[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { updates[key!!] = values }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { updates[key!!] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { updates[key!!] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { updates[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { updates[key!!] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { updates[key!!] = null }
            override fun clear(): SharedPreferences.Editor = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) values.clear()
                updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
