package com.myremote.app.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.myremote.app.domain.*
import com.myremote.app.google.GoogleTvDevice
import com.myremote.app.lg.LgDevice
import com.myremote.app.samsung.SamsungDevice
import com.myremote.app.hid.HidHost
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Screenshot regression harness. Renders production composables with synthetic state.
 * All callbacks are no-ops: never opens a physical adapter or issues a hardware command.
 */
@RunWith(Parameterized::class)
class CouchPilotScreenshotTest(private val scenario: String) {
    @get:Rule val rule = createComposeRule()
    @Test fun capture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val viewScenario = scenario.substringBefore("-font-")
        val scale = when { scenario.endsWith("150") -> 1.5f; scenario.endsWith("200") -> 2f; else -> 1f }
        val hebrew = scenario.startsWith("hebrew")
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(if (hebrew) "he" else "en"))
            fontScale = scale
        })
        val connected = RemoteState(activeDevice = ActiveDevice.STREAMER, selectedInput = InputSource.XIAOMI,
            tvConnection = ConnectionState.CONNECTED, streamerConnection = ConnectionState.CONNECTED,
            soundbarConnection = ConnectionState.CONNECTED)
        val state = when (scenario) {
            "main-busy", "main-navigation-busy" -> connected.copy(busyDevices = setOf(CommandDevice.STREAMER))
            "main-default" -> RemoteState(tvConnection = ConnectionState.NOT_CONFIGURED,
                streamerConnection = ConnectionState.NOT_CONFIGURED, soundbarConnection = ConnectionState.NOT_CONFIGURED)
            "main-lg", "main-ps5-context" -> connected.copy(activeDevice = ActiveDevice.TV,
                selectedInput = if (scenario == "main-lg") InputSource.MAC_MINI else InputSource.PS5)
            "error-top", "error-bottom" -> connected.copy(tvConnection = ConnectionState.ERROR,
                streamerConnection = ConnectionState.DISCONNECTED, soundbarConnection = ConnectionState.DISCONNECTED,
                errorMessage = "synthetic-unavailable", failure = FailureKind.NOT_CONNECTED)
            else -> connected
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density, scale)) {
                RemoteTheme {
                    RemoteScreen(state, {}, connectionSessionActive = scenario != "paused")
                    when (viewScenario) {
                        "lg-setup", "lg-pairing", "lg-connected", "lg-authorization" -> LgSetupDialog(
                            listOf(LgDevice("Review LG", "192.0.2.10", "55UK6700YVD")),
                            when (viewScenario) {
                                "lg-pairing" -> ConnectionState.PAIRING
                                "lg-connected" -> ConnectionState.CONNECTED
                                "lg-authorization" -> ConnectionState.AUTHORIZATION_REQUIRED
                                else -> ConnectionState.DISCOVERING
                            }, null, {}, {}, {}, {}, {}, {})
                        "xiaomi-discovery", "xiaomi-code", "xiaomi-bt", "xiaomi-bt-connected" -> GoogleTvSetupDialog(
                            listOf(GoogleTvDevice("Review Xiaomi", "192.0.2.20")),
                            if (viewScenario == "xiaomi-code") ConnectionState.WAITING_FOR_CODE else if (viewScenario.endsWith("connected")) ConnectionState.CONNECTED else ConnectionState.DISCOVERING,
                            null, {}, {}, {}, {}, {}, {}, bluetoothMode = viewScenario.startsWith("xiaomi-bt"),
                            bluetoothContent = { HidSetupContent(listOf(HidHost("Review Xiaomi", "02:00:00:00:00:01")),
                                true, true, true, false, null, {}, {}, {}, bonded = viewScenario.endsWith("connected"),
                                connection = if (viewScenario.endsWith("connected")) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED) })
                        "samsung-setup", "samsung-permission" -> SamsungSetupDialog(
                            listOf(SamsungDevice("Review Samsung M360", "02:00:00:00:00:02")),
                            ConnectionState.DISCONNECTED, null, viewScenario == "samsung-setup", {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.waitForIdle()
        when (scenario) {
            "settings", "hebrew-settings" -> rule.onNodeWithTag("remote_settings").performClick()
            "help" -> { rule.onNodeWithTag("remote_settings").performClick(); rule.onNodeWithText(localized.getString(com.myremote.app.R.string.remote_help)).performScrollTo().performClick() }
            "hebrew-navigation", "main-navigation", "main-navigation-busy", "font-150", "font-200", "hebrew-font-150", "hebrew-font-200", "landscape" -> rule.onNodeWithTag("fast_forward").performScrollTo()
            "main-keypad" -> rule.onNodeWithTag("digit_1").performScrollTo()
            "error-bottom" -> rule.onNodeWithTag("digit_0").performScrollTo()
        }
        rule.waitForIdle()
        if (scenario.endsWith("-font-200") && !scenario.startsWith("hebrew")) {
            rule.onNodeWithText(localized.getString(com.myremote.app.R.string.close)).performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithTag("watch_yes").assertDoesNotExist()
        if (scenario == "main-ps5-context") rule.onNodeWithTag("ok").assertDoesNotExist()
        Thread.sleep(400) // Allow the platform dialog/window compositor to finish before UIAutomation capture.
        val dir = File(context.getExternalFilesDir(null), "couchpilot-shots").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir, "$scenario.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        File(dir, "$scenario-semantics.txt").writeText(rule.onAllNodes(isRoot()).fetchSemanticsNodes().joinToString("\n") { it.toString() })
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases() = listOf(
            "main-default", "main-lg", "main-xiaomi", "main-ps5-context", "main-keypad", "main-navigation",
            "main-busy", "main-navigation-busy",
            "error-top", "error-bottom", "settings", "help", "paused", "lg-setup", "lg-pairing",
            "lg-connected", "lg-authorization", "xiaomi-discovery", "xiaomi-code", "xiaomi-bt",
            "xiaomi-bt-connected", "samsung-setup", "samsung-permission", "hebrew-main", "hebrew-navigation",
            "hebrew-settings", "font-150", "font-200", "landscape", "hebrew-font-150", "hebrew-font-200", "lg-setup-font-200", "xiaomi-code-font-200", "samsung-setup-font-200").map { arrayOf(it) }
    }
}
