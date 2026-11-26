package com.gayatripatel.callbridge

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.billing.PurchaseSnapshot
import com.gayatripatel.callbridge.core.billing.PurchaseState
import com.gayatripatel.callbridge.core.call.CallNetwork
import com.gayatripatel.callbridge.core.call.CallState
import com.gayatripatel.callbridge.core.presentation.PlansPresenter
import com.gayatripatel.callbridge.core.presentation.RecentsFilter
import com.gayatripatel.callbridge.core.presentation.RecentsPresenter
import com.gayatripatel.callbridge.core.sample.SampleData
import com.gayatripatel.callbridge.ui.Tab
import com.gayatripatel.callbridge.ui.TabShell
import com.gayatripatel.callbridge.ui.dialer.DialerScreen
import com.gayatripatel.callbridge.ui.dialer.DialerUiState
import com.gayatripatel.callbridge.ui.incall.InCallScreen
import com.gayatripatel.callbridge.ui.incall.inCallUiState
import com.gayatripatel.callbridge.ui.messages.ConversationScreen
import com.gayatripatel.callbridge.ui.messages.MessagesScreen
import com.gayatripatel.callbridge.ui.messages.conversationUiState
import com.gayatripatel.callbridge.ui.messages.threadRows
import com.gayatripatel.callbridge.ui.plans.PlansScreen
import com.gayatripatel.callbridge.ui.plans.PlansUiState
import com.gayatripatel.callbridge.ui.recents.RecentsScreen
import com.gayatripatel.callbridge.ui.recents.RecentsUiState
import com.gayatripatel.callbridge.ui.settings.SettingsScreen
import com.gayatripatel.callbridge.ui.settings.SettingsUiState
import com.gayatripatel.callbridge.ui.signup.VerifyScreen
import com.gayatripatel.callbridge.ui.signup.VerifyUiState
import com.gayatripatel.callbridge.ui.theme.CallBridgeTheme
import com.gayatripatel.callbridge.ui.voicemail.VoicemailScreen
import com.gayatripatel.callbridge.ui.voicemail.voicemailRows
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen on the JVM with Robolectric + Roborazzi. CI commits the PNGs to docs/screenshots,
 * so the README always shows the current UI. Data is the deterministic sample set at a fixed clock.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ScreenshotTest {

    private val now = SampleData.FIXED_NOW
    private val zone = SampleData.ZONE
    private val lines = SampleData.lines

    private fun shot(name: String, dark: Boolean = false, tab: Tab? = null, content: @Composable () -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            CallBridgeTheme(darkTheme = dark) {
                if (tab != null) TabShell(selected = tab, onSelect = {}) { m -> Box(m) { content() } } else content()
            }
        }
    }

    private fun recents(filter: RecentsFilter) = RecentsUiState(
        RecentsPresenter.present(SampleData.calls(now), lines, filter, now, zone), filter, lines.first(),
    )

    @Test fun recents_light() = shot("01_recents", tab = Tab.CALLS) { RecentsScreen(recents(RecentsFilter.ALL), {}, {}, {}) }

    @Test fun recents_dark() = shot("02_recents_dark", dark = true, tab = Tab.CALLS) { RecentsScreen(recents(RecentsFilter.ALL), {}, {}, {}) }

    @Test fun recents_missed() = shot("03_recents_missed", tab = Tab.CALLS) { RecentsScreen(recents(RecentsFilter.MISSED), {}, {}, {}) }

    @Test fun dialer() = shot("04_dialer") {
        DialerScreen(DialerUiState("4155550", SampleData.contacts[0], lines, "work"), {}, {}, {}, {}, {}, {})
    }

    @Test fun in_call_wifi() = shot("05_in_call") {
        val state = CallState.Active("+14155550134", now - 754_000, CallNetwork.WIFI, muted = true)
        InCallScreen(inCallUiState(state, "Maya Chen", "+14155550134", now), {}, {}, {}, {})
    }

    @Test fun in_call_reconnecting() = shot("06_in_call_reconnecting") {
        val active = CallState.Active("+12125550147", now - 95_000, CallNetwork.WIFI)
        InCallScreen(inCallUiState(CallState.Reconnecting(active, now - 2_000), "Daniel Brooks", "+12125550147", now), {}, {}, {}, {})
    }

    @Test fun messages() = shot("07_messages", tab = Tab.MESSAGES) {
        MessagesScreen(threadRows(SampleData.threads(now), lines, now, zone)) {}
    }

    @Test fun group_conversation() = shot("08_group_conversation") {
        val thread = SampleData.threads(now).single { it.id == "t-launch" }
        ConversationScreen(conversationUiState(thread, SampleData.messages(thread.id, now), ""), zone, {}, {}, {}, {})
    }

    @Test fun voicemail() = shot("09_voicemail", tab = Tab.VOICEMAIL) {
        VoicemailScreen(voicemailRows(SampleData.voicemails(now), lines, now, zone), {}, {})
    }

    @Test fun plans_from_web_funnel() = shot("10_plans") {
        val cards = PlansPresenter.present(EntitlementResolver.resolve(emptyList()), emptyMap(), "callbridge_pro_monthly")
        PlansScreen(PlansUiState(cards, "No active plan", true, "maya@example.com"), {}, {})
    }

    @Test fun plans_upgrade() = shot("11_plans_upgrade", dark = true) {
        val pro = EntitlementResolver.resolve(listOf(PurchaseSnapshot("callbridge_pro_monthly", "t", PurchaseState.PURCHASED, true, true, 0)))
        PlansScreen(PlansUiState(PlansPresenter.present(pro, emptyMap(), null), PlansPresenter.entitlementLabel(pro)), {}, {})
    }

    @Test fun verify_number() = shot("12_verify_otp") {
        VerifyScreen(VerifyUiState(phone = "4155550134", code = "4829", waitingForSms = true), {}, {}, {}, {})
    }

    @Test fun settings() = shot("13_settings", tab = Tab.SETTINGS) {
        val pro = EntitlementResolver.resolve(listOf(PurchaseSnapshot("callbridge_pro_monthly", "t", PurchaseState.PURCHASED, true, true, 0)))
        SettingsScreen(
            SettingsUiState(
                com.gayatripatel.callbridge.core.model.CallSettings(
                    forwardingEnabled = true, forwardingNumber = "+12125550147", blockedNumbers = listOf("+19005550110", "+1900*"),
                ),
                PlansPresenter.entitlementLabel(pro), lines,
            ),
            {}, {}, {},
        )
    }
}
