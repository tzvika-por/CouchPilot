package com.myremote.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.myremote.app.domain.ConnectionState
import com.myremote.app.samsung.SamsungDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SamsungSetupTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun permissionIsExplicitAndSetupDoesNotConnectBeforeSelection() {
        var permissions = 0
        composeRule.setContent { RemoteTheme {
            SamsungSetupDialog(emptyList(), ConnectionState.NOT_CONFIGURED, null, false,
                onPermission = { permissions++ }, onDevice = { fail("No device selected") },
                onRetry = {}, onSettings = {}, onForget = {}, onDismiss = {})
        } }
        composeRule.onNodeWithTag("samsung_permission").performClick()
        assertEquals(1, permissions)
        composeRule.onNodeWithTag("samsung_device").assertDoesNotExist()
    }
    @Test fun selectsExistingBondWithoutNewPairing() {
        val device = SamsungDevice("HW-M360", "02:00:00:00:00:01")
        var selected: SamsungDevice? = null
        composeRule.setContent { RemoteTheme {
            SamsungSetupDialog(listOf(device), ConnectionState.NOT_CONFIGURED, null, true,
                onPermission = {}, onDevice = { selected = it }, onRetry = {}, onSettings = {}, onForget = {}, onDismiss = {})
        } }
        composeRule.onNodeWithTag("samsung_device").performScrollTo().performClick()
        assertEquals(device, selected)
    }
}
