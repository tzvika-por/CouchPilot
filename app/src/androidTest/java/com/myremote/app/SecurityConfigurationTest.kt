package com.myremote.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.lg.LgDescription
import com.myremote.app.lg.LgDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class SecurityConfigurationTest {
    @Test fun androidKeystoreMigratesLegacyLgGrantAndAuthenticatesCiphertext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("security_test_lg_credentials", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putString("host", "192.0.2.1").putString("name", "Test LG")
            .putString("client_key", "isolated-test-grant").putString("certificate_pin", "test-pin").commit()
        try {
            val store = com.myremote.app.lg.LgPairingStore(prefs, com.myremote.app.lg.LgCredentialCipher.android())
            assertEquals("isolated-test-grant", store.read()!!.clientKey)
            assertFalse(prefs.contains("client_key"))
            assertEquals("isolated-test-grant", com.myremote.app.lg.LgPairingStore(prefs,
                com.myremote.app.lg.LgCredentialCipher.android()).read()!!.clientKey)
            val bytes = java.util.Base64.getDecoder().decode(prefs.getString("client_key_encrypted", null))
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            prefs.edit().putString("client_key_encrypted", java.util.Base64.getEncoder().encodeToString(bytes)).commit()
            assertTrue(store.read()!!.authorizationNeedsRefresh)
            assertNull(store.read()!!.clientKey)
            assertEquals("test-pin", store.read()!!.certificatePin)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun backupRulesExcludeCredentialsAcrossEveryStorageDomain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val required = setOf("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")
        for (resource in listOf(R.xml.full_backup_content, R.xml.data_extraction_rules)) {
            context.resources.getXml(resource).use { parser ->
                val excluded = mutableMapOf<String, MutableSet<String>>()
                var section = "legacy"
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.name in listOf("cloud-backup", "device-transfer")) section = parser.name
                        if (parser.name == "exclude" && parser.getAttributeValue(null, "path") == ".")
                            excluded.getOrPut(section) { mutableSetOf() }.add(parser.getAttributeValue(null, "domain"))
                    }
                    parser.next()
                }
                assertEquals(if (resource == R.xml.full_backup_content) setOf("legacy") else setOf("cloud-backup", "device-transfer"), excluded.keys)
                excluded.values.forEach { assertEquals(required, it) }
            }
        }
        assertEquals(0, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun androidDescriptionParserAcceptsMetadataAndRejectsDoctype() {
        val base = LgDevice("LG", "192.0.2.1")
        assertEquals("Living room", LgDescription.parse(base, "<root><friendlyName>Living room</friendlyName></root>".toByteArray()).name)
        val malicious = """<!DOCTYPE root [<!ENTITY leak SYSTEM "file:///data/local/tmp/nonexistent">]><root><friendlyName>&leak;</friendlyName></root>"""
        assertThrows(Exception::class.java) { LgDescription.parse(base, malicious.toByteArray()) }
    }
}
