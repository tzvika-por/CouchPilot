package com.myremote.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.myremote.app.domain.ConnectionState
import com.myremote.app.hid.HidHost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HidSetupTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun accessPrecedesVisibilityAndRegistrationEnablesSetup() {
        var requests = 0
        composeRule.setContent { RemoteTheme { Column {
            HidSetupContent(emptyList(), false, true, false, null, null,
                onPermission = { requests++ }, onVisible = { fail("No permission") }, onHost = {})
        } } }
        composeRule.onNodeWithTag("hid_permission").performClick()
        assertEquals(1, requests)
        composeRule.onNodeWithTag("hid_visible").assertDoesNotExist()
    }
    @Test fun bondedTvSelectionAndLanReturnAreExplicit() {
        val host = HidHost("Synthetic TV", "02:00:00:00:00:01")
        var selected: HidHost? = null; var lan = 0; var visible = 0
        composeRule.setContent { RemoteTheme {
            GoogleTvSetupDialog(emptyList(), ConnectionState.CONNECTING, null, {}, {}, {}, {}, {}, {},
                bluetoothMode = true, onLan = { lan++ }, bluetoothContent = {
                    HidSetupContent(listOf(host), true, true, true, "Synthetic phone", null,
                        onPermission = {}, onVisible = { visible++ }, onHost = { selected = it })
                })
        } }
        composeRule.onNodeWithTag("manual_host").assertDoesNotExist()
        composeRule.onNodeWithTag("hid_visible").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("hid_host").performScrollTo().performClick()
        assertEquals(host, selected); assertEquals(1, visible)
        composeRule.onNodeWithTag("streamer_lan").performScrollTo().performClick()
        assertEquals(1, lan)
    }
    @Test fun visibilityCannotStartBeforeProfileRegistration() {
        composeRule.setContent { RemoteTheme { Column {
            HidSetupContent(emptyList(), true, true, false, "Synthetic phone", null,
                onPermission = {}, onVisible = { fail("Not registered") }, onHost = {})
        } } }
        composeRule.onNodeWithTag("hid_visible").assertIsNotEnabled()
    }
}
