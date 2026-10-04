package com.myremote.app.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.myremote.app.domain.RemoteAction
import com.myremote.app.domain.RemoteState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RemoteScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun requiredControlsAreAvailableAndWatchYesDispatchesOneAction() {
        val actions = mutableListOf<RemoteAction>()
        composeRule.setContent {
            RemoteTheme { RemoteScreen(RemoteState(), onAction = { actions.add(it) }) }
        }

        listOf("power", "watch_yes", "source_ps5", "source_mac_mini", "source_xiaomi",
            "source_pc", "volume_down", "mute", "volume_up", "channel_down", "last_channel",
            "channel_up", "digit_0", "digit_9", "up", "down", "left", "right", "ok",
            "back", "home", "play_pause", "rewind", "fast_forward", "configure_xiaomi", "configure_samsung").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule.onNodeWithTag("watch_yes").performScrollTo().performClick()
        assertEquals(listOf(RemoteAction.WatchYesPlus), actions)
    }
    @Test fun rtlDoesNotReverseNavigationIntentAndMediaButtonsDispatch() {
        val actions = mutableListOf<RemoteAction>()
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                RemoteTheme { RemoteScreen(RemoteState(), onAction = { actions += it }) }
            }
        }
        composeRule.onNodeWithTag("left").performScrollTo().performClick()
        composeRule.onNodeWithTag("rewind").performScrollTo().performClick()
        composeRule.onNodeWithTag("fast_forward").performScrollTo().performClick()
        assertEquals(listOf(RemoteAction.Key(com.myremote.app.domain.RemoteKey.LEFT),
            RemoteAction.Key(com.myremote.app.domain.RemoteKey.REWIND),
            RemoteAction.Key(com.myremote.app.domain.RemoteKey.FAST_FORWARD)), actions)
    }
    @Test fun protocolErrorIsNeverRenderedInMainRemote() {
        composeRule.setContent {
            RemoteTheme { RemoteScreen(RemoteState(errorMessage = "raw protocol error 401"), onAction = {}) }
        }
        composeRule.onNodeWithTag("action_error").assertExists()
        composeRule.onNodeWithText("raw protocol error 401", substring = true).assertDoesNotExist()
    }
}
