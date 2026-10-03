package com.myremote.app.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
            "back", "home", "play_pause").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule.onNodeWithTag("watch_yes").performClick()
        assertEquals(listOf(RemoteAction.WatchYesPlus), actions)
    }
}
