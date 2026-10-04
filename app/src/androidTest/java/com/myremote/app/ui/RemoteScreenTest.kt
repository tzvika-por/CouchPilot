package com.myremote.app.ui

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.R
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

    @Test fun requiredControlsExposeIndependentDevicePowerAndRemoveRedundantShortcut() {
        val actions = mutableListOf<RemoteAction>()
        composeRule.setContent {
            RemoteTheme { RemoteScreen(RemoteState(), onAction = { actions.add(it) }) }
        }

        listOf("power", "xiaomi_off", "soundbar_power", "source_ps5", "source_mac_mini", "source_xiaomi",
            "source_pc", "volume_down", "mute", "volume_up", "channel_down", "last_channel",
            "channel_up", "digit_0", "digit_9", "up", "down", "left", "right", "ok",
            "back", "home", "play_pause", "rewind", "fast_forward", "configure_xiaomi", "configure_samsung").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule.onNodeWithTag("watch_yes").assertDoesNotExist()
        composeRule.onNodeWithTag("xiaomi_off").performScrollTo().performClick()
        composeRule.onNodeWithTag("soundbar_power").performScrollTo().performClick()
        assertEquals(listOf(RemoteAction.StreamerOff, RemoteAction.SoundbarPower), actions)
    }
    @Test fun rtlPowerLabelsIdentifyActiveTargetAndIndependentOffActions() {
        val state = mutableStateOf(RemoteState(activeDevice = com.myremote.app.domain.ActiveDevice.TV))
        val actions = mutableListOf<RemoteAction>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                RemoteTheme { RemoteScreen(state.value, onAction = { actions += it }) }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.tv_power)).assertExists()
        composeRule.onNodeWithTag("xiaomi_off").performScrollTo().performClick()
        composeRule.runOnIdle { state.value = state.value.copy(activeDevice = com.myremote.app.domain.ActiveDevice.STREAMER) }
        composeRule.onNodeWithText(context.getString(R.string.streamer_power)).assertExists()
        composeRule.onNodeWithTag("soundbar_power").performScrollTo().performClick()
        assertEquals(listOf(RemoteAction.StreamerOff, RemoteAction.SoundbarPower), actions)
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
    @Test fun soundControlsAreAccessibleIconsAndMuteFollowsReportedState() {
        val state = mutableStateOf(RemoteState())
        val actions = mutableListOf<RemoteAction>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            RemoteTheme { RemoteScreen(state.value, onAction = { actions += it }) }
        }
        composeRule.onNodeWithTag("volume_down").assertContentDescriptionEquals(context.getString(R.string.volume_down))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("volume_up").assertContentDescriptionEquals(context.getString(R.string.volume_up))
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("mute").assertContentDescriptionEquals(context.getString(R.string.mute_toggle))
            .performScrollTo().performClick()
        composeRule.runOnIdle { state.value = state.value.copy(soundbarMuted = true) }
        composeRule.onNodeWithTag("mute").assertContentDescriptionEquals(context.getString(R.string.unmute))
            .performScrollTo().performClick()
        composeRule.runOnIdle { state.value = state.value.copy(soundbarMuted = false) }
        composeRule.onNodeWithTag("mute").assertContentDescriptionEquals(context.getString(R.string.mute))
        for (label in listOf(R.string.volume_down, R.string.volume_up, R.string.mute, R.string.unmute)) {
            composeRule.onNodeWithText(context.getString(label)).assertDoesNotExist()
        }
        assertEquals(listOf(RemoteAction.VolumeDown, RemoteAction.VolumeUp, RemoteAction.Mute, RemoteAction.Mute), actions)
    }

    @Test fun protocolErrorIsNeverRenderedInMainRemote() {
        composeRule.setContent {
            RemoteTheme { RemoteScreen(RemoteState(errorMessage = "raw protocol error 401"), onAction = {}) }
        }
        composeRule.onNodeWithTag("action_error").assertExists()
        composeRule.onNodeWithText("raw protocol error 401", substring = true).assertDoesNotExist()
    }
}
