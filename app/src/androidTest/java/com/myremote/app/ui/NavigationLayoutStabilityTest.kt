package com.myremote.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myremote.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Production layout and touch interactions; synthetic command completion needs no hardware. */
@RunWith(Parameterized::class)
class NavigationLayoutStabilityTest(private val fontPercent: Int) {
    @get:Rule val rule = createComposeRule()

    @Test fun pressFeedbackNeverMovesRemote() {
        val state = mutableStateOf(RemoteState(selectedInput = InputSource.XIAOMI,
            activeDevice = ActiveDevice.STREAMER, tvConnection = ConnectionState.CONNECTED,
            streamerConnection = ConnectionState.CONNECTED, soundbarConnection = ConnectionState.CONNECTED))
        val actions = mutableListOf<RemoteAction>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontPercent / 100f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                RemoteTheme {
                    RemoteScreen(state.value, { action ->
                        actions += action
                        state.value = state.value.copy(busyDevices = setOf(CommandDevice.STREAMER))
                    })
                }
            }
        }
        val controls = linkedMapOf("up" to RemoteKey.UP, "down" to RemoteKey.DOWN,
            "left" to RemoteKey.LEFT, "right" to RemoteKey.RIGHT, "ok" to RemoteKey.CENTER,
            "back" to RemoteKey.BACK, "home" to RemoteKey.HOME, "play_pause" to RemoteKey.PLAY_PAUSE,
            "rewind" to RemoteKey.REWIND, "fast_forward" to RemoteKey.FAST_FORWARD)
        val anchors = controls.keys + listOf("remote_scroll", "navigation_panel", "active_device",
            "power", "configure_lg", "configure_xiaomi", "configure_samsung", "source_xiaomi",
            "volume_up", "mute", "digit_0")
        fun positions() = anchors.associateWith { tag ->
            rule.onNodeWithTag(tag).fetchSemanticsNode().let { it.positionInRoot to it.size }
        }
        fun scrollOffset(): Float = rule.onNodeWithTag("remote_scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun completeCommand() = rule.runOnIdle {
            state.value = state.value.copy(busyDevices = emptySet(), actionCount = state.value.actionCount + 1)
        }
        controls.forEach { (tag, key) ->
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val baseline = positions()
            val scroll = scrollOffset()
            repeat(2) {
                rule.onNodeWithTag(tag).performTouchInput { down(center) }
                assertEquals("$tag during press", baseline, positions())
                assertEquals("$tag pressed scroll", scroll, scrollOffset())
                rule.onNodeWithTag(tag).performTouchInput { up() }
                rule.runOnIdle { assertEquals(RemoteAction.Key(key), actions.last()) }
                rule.onNodeWithTag("command_progress").assertIsDisplayed()
                assertEquals("$tag executing", baseline, positions())
                assertEquals("$tag executing scroll", scroll, scrollOffset())
                completeCommand()
                rule.onNodeWithTag("command_progress").assertDoesNotExist()
                assertEquals("$tag completed", baseline, positions())
                assertEquals("$tag completed scroll", scroll, scrollOffset())
            }
            val count = actions.size
            rule.onNodeWithTag(tag).performTouchInput { repeat(5) { click() } }
            rule.runOnIdle {
                assertEquals(List(5) { RemoteAction.Key(key) }, actions.drop(count))
            }
            assertEquals("$tag rapid presses", baseline, positions())
            assertEquals("$tag rapid scroll", scroll, scrollOffset())
            completeCommand()
            assertEquals("$tag rapid completion", baseline, positions())
        }
        assertEquals(70, actions.size)
        // A failure and the next successful command clearing it must also be layout-neutral.
        rule.onNodeWithTag("digit_0").performScrollTo()
        val baseline = positions()
        val scroll = scrollOffset()
        rule.runOnIdle {
            state.value = state.value.copy(errorMessage = "synthetic failure", failure = FailureKind.NOT_CONNECTED)
        }
        rule.onNodeWithTag("action_error").assertIsDisplayed()
        assertEquals("failure feedback", baseline, positions())
        assertEquals("failure scroll", scroll, scrollOffset())
        rule.onNodeWithTag("dismiss_action_error").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.onNodeWithTag("action_error").assertDoesNotExist()
        assertEquals("dismiss feedback", baseline, positions())
        assertEquals("dismiss scroll", scroll, scrollOffset())
        rule.runOnIdle {
            state.value = state.value.copy(errorMessage = null, failure = null)
        }
        assertEquals("error recovery", baseline, positions())
        assertEquals("error recovery scroll", scroll, scrollOffset())
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = listOf(100, 150, 200).map { arrayOf(it) }
    }
}
