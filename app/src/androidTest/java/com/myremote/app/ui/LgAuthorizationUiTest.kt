package com.myremote.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.myremote.app.domain.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LgAuthorizationUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun authorizationRefreshIsAvailableAndProtocolErrorIsHidden() {
        var refreshes = 0
        val protocolError = "{raw protocol error}"
        composeRule.setContent {
            RemoteTheme {
                LgSetupDialog(devices = emptyList(), connection = ConnectionState.AUTHORIZATION_REQUIRED,
                    error = protocolError, onDismiss = {}, onDevice = {}, onManualHost = {},
                    onRetry = {}, onForget = {}, onRefreshAuthorization = { refreshes++ })
            }
        }
        composeRule.onNodeWithText(protocolError).assertDoesNotExist()
        composeRule.onNodeWithTag("lg_refresh_authorization").performClick()
        assertEquals(1, refreshes)
    }
}
