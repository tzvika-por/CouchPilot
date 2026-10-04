package com.myremote.app.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.R
import com.myremote.app.domain.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** All visual connection statuses here are fixtures. No physical device or adapter is invoked. */
class ReferenceUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val connected = RemoteState(activeDevice = ActiveDevice.STREAMER, selectedInput = InputSource.XIAOMI,
        tvConnection = ConnectionState.CONNECTED, streamerConnection = ConnectionState.CONNECTED,
        soundbarConnection = ConnectionState.CONNECTED)

    @Test fun settingsDeviceSelectionAndConnectionManagementDoNotDispatchRemoteCommands() {
        val actions = mutableListOf<RemoteAction>()
        var lg = 0
        var xiaomi = 0
        var samsung = 0
        var connections = 0
        val active = mutableStateOf(true)
        composeRule.setContent { RemoteTheme {
            RemoteScreen(connected, { actions += it }, { xiaomi++ }, { lg++ }, { samsung++ },
                active.value, { connections++; active.value = !active.value })
        } }
        composeRule.onNodeWithTag("remote_settings").performClick()
        composeRule.onNodeWithText(context.getString(R.string.configure_lg)).performClick()
        assertEquals(1, lg)
        composeRule.onNodeWithTag("configure_xiaomi").performClick()
        composeRule.onNodeWithTag("configure_samsung").performClick()
        assertEquals(1, xiaomi); assertEquals(1, samsung)
        composeRule.onNodeWithTag("remote_settings").performClick()
        composeRule.onNodeWithTag("connection_session").performClick()
        assertEquals(1, connections)
        composeRule.onAllNodesWithText(context.getString(R.string.remote_paused_guidance)).assertCountEquals(2)
        composeRule.onNodeWithText(context.getString(R.string.close)).performClick()
        composeRule.onNodeWithTag("resume_session").performScrollTo().performClick()
        assertEquals(2, connections)
        assertEquals(emptyList<RemoteAction>(), actions)
    }

    @Test fun hebrewReferenceLayoutHasRealSourcesSelectedStateAndReachableCircularControls() {
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("he"))
        })
        val actions = mutableListOf<RemoteAction>()
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                RemoteTheme { RemoteScreen(connected, { actions += it }, connectionSessionActive = true) }
            }
        }
        composeRule.onNodeWithTag("source_xiaomi").assertIsSelected()
        composeRule.onNodeWithTag("source_mac_mini").assertIsNotSelected()
        composeRule.onNodeWithTag("source_tv").assertDoesNotExist()
        composeRule.onNodeWithTag("watch_yes").assertDoesNotExist()
        for (tag in listOf("source_ps5", "source_mac_mini", "source_xiaomi", "source_pc")) {
            composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        savePreview("ui-hebrew-top.png")
        for (tag in listOf("up", "down", "left", "right", "ok")) {
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(listOf(RemoteKey.UP, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.RIGHT, RemoteKey.CENTER)
            .map { RemoteAction.Key(it) }, actions)
        composeRule.onNodeWithTag("fast_forward").performScrollTo()
        savePreview("ui-hebrew-navigation.png")
    }

    @Test fun enlargedTextAndRtlKeepDigitsAndDirectionsReachableAndCorrect() {
        val actions = mutableListOf<RemoteAction>()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                RemoteTheme { RemoteScreen(connected, { actions += it }) }
            }
        }
        composeRule.onNodeWithTag("digit_7").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("right").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("home").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(RemoteAction.NumericKey(7), RemoteAction.Key(RemoteKey.RIGHT), RemoteAction.Key(RemoteKey.HOME)), actions)
    }

    private fun savePreview(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
