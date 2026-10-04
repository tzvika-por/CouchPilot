package com.myremote.app.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.domain.*
import com.myremote.app.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContextualRemoteTest {
    @get:Rule val rule = createComposeRule()
    @Test fun nonXiaomiSourcesHideNavigationChannelsDigitsAndMediaButRetainCommonControls() {
        val state = mutableStateOf(RemoteState())
        rule.setContent { RemoteTheme { RemoteScreen(state.value, {}) } }
        listOf(InputSource.PS5, InputSource.MAC_MINI, InputSource.PC).forEach { source ->
            rule.runOnIdle { state.value = state.value.copy(selectedInput = source) }
            listOf("up", "ok", "digit_0", "channel_up", "last_channel", "play_pause").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
            listOf("power", "source_xiaomi", "volume_up", "mute").forEach { rule.onNodeWithTag(it).assertExists() }
        }
        rule.runOnIdle { state.value = state.value.copy(selectedInput = InputSource.XIAOMI) }
        listOf("up", "ok", "digit_0", "channel_up", "last_channel", "play_pause").forEach { rule.onNodeWithTag(it).assertExists() }
    }

    @Test fun hebrewAt200PercentKeepsSourceStatusDirectionsAndManagementActionsUsable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("he")); fontScale = 2f })
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalLayoutDirection provides LayoutDirection.Rtl, LocalDensity provides Density(density.density, 2f)) {
                RemoteTheme { RemoteScreen(RemoteState(selectedInput = InputSource.XIAOMI), {}) }
            }
        }
        listOf("source_ps5", "source_xiaomi", "configure_lg", "configure_xiaomi", "configure_samsung", "ok", "left", "digit_0").forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        rule.onNodeWithTag("remote_settings").performScrollTo().performClick()
        rule.onNodeWithText(localized.getString(R.string.configure_lg)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(localized.getString(R.string.samsung_setup)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(localized.getString(R.string.close)).assertIsDisplayed().performClick()
    }

    @Test fun managementKeepsTvWakeAvailableWithoutChangingXiaomiSource() {
        val actions = mutableListOf<RemoteAction>()
        rule.setContent { RemoteTheme { RemoteScreen(RemoteState(selectedInput = InputSource.XIAOMI,
            activeDevice = ActiveDevice.STREAMER), { actions += it }) } }
        rule.onNodeWithTag("remote_settings").performClick()
        rule.onNodeWithTag("lg_power").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(RemoteAction.TvPower), actions)
    }

    @Test fun errorSnackbarIsVisibleWhileUserIsAtKeypad() {
        val state = mutableStateOf(RemoteState(selectedInput = InputSource.XIAOMI))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.setContent { RemoteTheme { RemoteScreen(state.value, {}) } }
        rule.onNodeWithTag("digit_0").performScrollTo()
        rule.runOnIdle { state.value = state.value.copy(errorMessage = "synthetic", failure = FailureKind.NOT_CONNECTED) }
        rule.onAllNodesWithText(context.getString(R.string.error_not_connected))[1].assertIsDisplayed()
    }
}
