package com.gayatripatel.callbridge.core

import com.gayatripatel.callbridge.core.billing.Entitlement
import com.gayatripatel.callbridge.core.call.CallEvent
import com.gayatripatel.callbridge.core.call.CallNetwork
import com.gayatripatel.callbridge.core.call.CallState
import com.gayatripatel.callbridge.core.call.CallStateMachine
import com.gayatripatel.callbridge.core.call.EndReason
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.billing.PlanCatalog
import com.gayatripatel.callbridge.core.billing.PlanChange
import com.gayatripatel.callbridge.core.billing.PurchaseSnapshot
import com.gayatripatel.callbridge.core.billing.PurchaseState
import com.gayatripatel.callbridge.core.link.DeepLinkParser
import com.gayatripatel.callbridge.core.link.Destination
import com.gayatripatel.callbridge.core.link.HandoffParser
import com.gayatripatel.callbridge.core.link.HandoffResult
import com.gayatripatel.callbridge.core.model.CallSettings
import com.gayatripatel.callbridge.core.otp.OtpParser
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.core.presentation.Durations
import com.gayatripatel.callbridge.core.presentation.PlansPresenter
import com.gayatripatel.callbridge.core.presentation.RecentsFilter
import com.gayatripatel.callbridge.core.presentation.RecentsPresenter
import com.gayatripatel.callbridge.core.presentation.TimeLabels
import com.gayatripatel.callbridge.core.sample.SampleData
import com.gayatripatel.callbridge.core.screening.CallScreener
import com.gayatripatel.callbridge.core.screening.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun `formats national numbers as you type`() {
        assertEquals("", PhoneNumbers.formatAsYouType(""))
        assertEquals("415", PhoneNumbers.formatAsYouType("415"))
        assertEquals("(415) 5", PhoneNumbers.formatAsYouType("4155"))
        assertEquals("(415) 555-0134", PhoneNumbers.formatAsYouType("4155550134"))
    }

    @Test fun `keeps trunk and country prefixes`() {
        assertEquals("1", PhoneNumbers.formatAsYouType("1"))
        assertEquals("1 (415) 555-0134", PhoneNumbers.formatAsYouType("14155550134"))
        assertEquals("+1 (415) 555-0134", PhoneNumbers.formatAsYouType("+14155550134"))
        assertEquals("+447911123456", PhoneNumbers.formatAsYouType("+44 7911 123456"))
    }

    @Test fun `normalises to E164 or rejects`() {
        assertEquals("+14155550134", PhoneNumbers.toE164("(415) 555-0134"))
        assertEquals("+14155550134", PhoneNumbers.toE164("1 415 555 0134"))
        assertEquals("+447911123456", PhoneNumbers.toE164("+44 7911 123456"))
        assertNull(PhoneNumbers.toE164("0155550134")) // area codes can't start with 0
        assertNull(PhoneNumbers.toE164("555"))
        assertEquals("(415) 555-0134", PhoneNumbers.display("+14155550134"))
    }
}

class CallStateMachineTest {
    private val m = CallStateMachine
    private val n = "+14155550134"

    @Test fun `outgoing call connects and ends with duration`() {
        var s: CallState = CallState.Idle
        s = m.reduce(s, CallEvent.Dial(n, CallNetwork.WIFI))
        s = m.reduce(s, CallEvent.RemoteAnswered(1_000))
        s = m.reduce(s, CallEvent.ToggleMute)
        assertTrue((s as CallState.Active).muted)
        s = m.reduce(s, CallEvent.Hangup(61_000))
        assertEquals(
            CallState.Ended(n, EndReason.COMPLETED, 60_000), s,
        )
    }

    @Test fun `wifi to mobile data is a seamless handover`() {
        val active = CallState.Active(n, 0, CallNetwork.WIFI)
        val s = m.reduce(active, CallEvent.NetworkChanged(CallNetwork.MOBILE_DATA, 5_000))
        assertEquals(active.copy(network = CallNetwork.MOBILE_DATA, handovers = 1), s)
    }

    @Test fun `lost network reconnects or drops after timeout`() {
        val active = CallState.Active(n, 0, CallNetwork.WIFI)
        val lost = m.reduce(active, CallEvent.NetworkChanged(null, 20_000))
        assertTrue(lost is CallState.Reconnecting)

        val back = m.reduce(lost, CallEvent.NetworkChanged(CallNetwork.MOBILE_DATA, 23_000))
        assertEquals(CallNetwork.MOBILE_DATA, (back as CallState.Active).network)

        assertEquals(lost, m.reduce(lost, CallEvent.Tick(29_999)))
        val dropped = m.reduce(lost, CallEvent.Tick(30_000))
        assertEquals(CallState.Ended(n, EndReason.DROPPED, 30_000), dropped)
    }

    @Test fun `unanswered incoming call is missed and stray events are ignored`() {
        val ringing = m.reduce(CallState.Idle, CallEvent.Incoming(n))
        assertEquals(ringing, m.reduce(ringing, CallEvent.ToggleMute))
        assertEquals(
            EndReason.MISSED,
            (m.reduce(ringing, CallEvent.RemoteHangup(9)) as CallState.Ended).reason,
        )
    }
}

class EntitlementTest {
    private fun p(id: String, state: PurchaseState = PurchaseState.PURCHASED, ack: Boolean = true, renew: Boolean = true, t: Long = 0) =
        PurchaseSnapshot(id, "tok-$id", state, ack, renew, t)

    @Test fun `no purchases means no access`() {
        assertEquals(Entitlement.None, EntitlementResolver.resolve(emptyList()))
    }

    @Test fun `highest tier wins during an upgrade overlap`() {
        val e = EntitlementResolver.resolve(listOf(p("callbridge_basic_monthly", t = 1), p("callbridge_business_monthly", ack = false, t = 2)))
        e as Entitlement.Active
        assertEquals("Business", e.plan.name)
        assertTrue(e.needsAcknowledgement)
    }

    @Test fun `pending purchases grant nothing and are never acknowledged`() {
        val purchases = listOf(p("callbridge_pro_monthly", PurchaseState.PENDING, ack = false))
        assertTrue(EntitlementResolver.resolve(purchases) is Entitlement.Pending)
        assertTrue(EntitlementResolver.tokensToAcknowledge(purchases).isEmpty())
    }

    @Test fun `unknown products are ignored`() {
        assertEquals(Entitlement.None, EntitlementResolver.resolve(listOf(p("legacy_sku"))))
    }

    @Test fun `plan change modes`() {
        val pro = EntitlementResolver.resolve(listOf(p("callbridge_pro_monthly")))
        val catalog = PlanCatalog
        assertEquals(PlanChange.UPGRADE_CHARGE_PRORATED, EntitlementResolver.planChange(pro, catalog.find("callbridge_business_monthly")!!))
        assertEquals(PlanChange.DOWNGRADE_DEFERRED, EntitlementResolver.planChange(pro, catalog.find("callbridge_basic_monthly")!!))
        assertEquals(PlanChange.SAME_TIER_SWITCH, EntitlementResolver.planChange(pro, catalog.find("callbridge_pro_annual")!!))
        assertEquals(PlanChange.NEW_PURCHASE, EntitlementResolver.planChange(Entitlement.None, catalog.find("callbridge_pro_annual")!!))
    }
}

class LinksTest {
    @Test fun `parses app links and custom scheme`() {
        assertEquals(Destination.Dial("+14155550134"), DeepLinkParser.parse("callbridge://dial?number=%28415%29%20555-0134"))
        assertEquals(Destination.Dial("+14155550134"), DeepLinkParser.parse("https://callbridge.example.com/call/4155550134"))
        assertEquals(Destination.Conversation("t-maya"), DeepLinkParser.parse("https://callbridge.example.com/messages/t-maya"))
        assertEquals(Destination.Plans("callbridge_pro_monthly"), DeepLinkParser.parse("https://callbridge.example.com/plans?plan=callbridge_pro_monthly"))
        assertEquals(Destination.Voicemail, DeepLinkParser.parse("callbridge://voicemail"))
    }

    @Test fun `rejects foreign hosts and unsafe ids`() {
        assertNull(DeepLinkParser.parse("https://evil.example.com/messages/t-maya"))
        assertNull(DeepLinkParser.parse("https://callbridge.example.com/messages/..%2Fsecret"))
        assertNull(DeepLinkParser.parse("callbridge://dial?number=12"))
        assertNull(DeepLinkParser.parse("not a url"))
    }

    private val ids = PlanCatalog.productIds
    private val token = "s3ss10n_ABCDEFGHIJKLmnop"

    @Test fun `web funnel handoff is validated`() {
        assertEquals(
            HandoffResult.Valid(token, "callbridge_pro_monthly", "maya@example.com"),
            HandoffParser.parse("https://callbridge.example.com/app/handoff?session=$token&plan=callbridge_pro_monthly&email=maya%40example.com", ids),
        )
        assertEquals(HandoffResult.NotHandoff, HandoffParser.parse("https://callbridge.example.com/signup/step2", ids))
        assertTrue(HandoffParser.parse("http://callbridge.example.com/app/handoff?session=$token&plan=callbridge_pro_monthly", ids) is HandoffResult.Invalid)
        assertTrue(HandoffParser.parse("https://callbridge.example.com/app/handoff?session=short&plan=callbridge_pro_monthly", ids) is HandoffResult.Invalid)
        assertTrue(HandoffParser.parse("https://callbridge.example.com/app/handoff?session=$token&plan=free_forever", ids) is HandoffResult.Invalid)
    }
}

class ScreeningAndOtpTest {
    private val n = "+18885550123"

    @Test fun `block list beats everything, contacts beat spam score`() {
        val s = CallSettings(blockedNumbers = listOf("+1888*"))
        assertEquals(Verdict.BLOCK, CallScreener.decide(n, isContact = true, spamScore = 0, settings = s).verdict)
        assertEquals(Verdict.RING, CallScreener.decide(n, isContact = true, spamScore = 99, settings = CallSettings()).verdict)
    }

    @Test fun `spam score and unknown caller screening`() {
        assertEquals(Verdict.BLOCK, CallScreener.decide(n, false, 97, CallSettings()).verdict)
        val vm = CallScreener.decide(n, false, 80, CallSettings())
        assertEquals(Verdict.SEND_TO_VOICEMAIL, vm.verdict)
        assertEquals("Likely spam", vm.label)
        assertEquals(Verdict.RING, CallScreener.decide(n, false, null, CallSettings()).verdict)
        assertEquals(Verdict.SEND_TO_VOICEMAIL, CallScreener.decide(n, false, null, CallSettings(screenUnknownCallers = true)).verdict)
    }

    @Test fun `extracts the code and ignores the app hash and other numbers`() {
        assertEquals("482913", OtpParser.parse("<#> Your CallBridge code is 482913\nFA+9qCX9VSu"))
        assertEquals("120934", OtpParser.parse("Order 551234 shipped. Your verification code: 120934"))
        assertEquals("7731", OtpParser.parse("PIN 7731", length = 4))
        assertNull(OtpParser.parse("No code here"))
    }
}

class PresentationTest {
    private val now = SampleData.FIXED_NOW
    private val zone = SampleData.ZONE

    @Test fun `relative time labels`() {
        assertEquals("Just now", TimeLabels.relative(now - 10_000, now, zone))
        assertEquals("12 min ago", TimeLabels.relative(now - 12 * 60_000, now, zone))
        assertEquals("12:30 PM", TimeLabels.relative(now - 3 * 3_600_000, now, zone))
        assertEquals("Yesterday", TimeLabels.relative(now - 24 * 3_600_000, now, zone))
        assertEquals("Wed", TimeLabels.relative(now - 2 * 24 * 3_600_000, now, zone))
        assertEquals("Nov 11", TimeLabels.relative(now - 9 * 24 * 3_600_000, now, zone))
    }

    @Test fun `durations`() {
        assertEquals("0:05", Durations.format(5))
        assertEquals("12:34", Durations.format(754))
        assertEquals("1:02:05", Durations.format(3725))
    }

    @Test fun `consecutive missed calls from the same caller collapse`() {
        val rows = RecentsPresenter.present(SampleData.calls(now), SampleData.lines, RecentsFilter.ALL, now, zone)
        val priya = rows.single { it.title == "Priya Raman" }
        assertEquals(2, priya.count)
        assertEquals(SampleData.calls(now).size - 1, rows.size)

        val missed = RecentsPresenter.present(SampleData.calls(now), SampleData.lines, RecentsFilter.MISSED, now, zone)
        assertEquals(listOf("(888) 555-0123", "Priya Raman"), missed.map { it.title })
    }
}

class PlansPresenterTest {
    @Test fun `actions follow the current entitlement`() {
        val none = PlansPresenter.present(Entitlement.None, emptyMap(), "callbridge_pro_monthly")
        assertTrue(none.all { it.actionLabel == "Subscribe" && it.actionEnabled })
        assertEquals("$15.99", none.single { it.highlighted }.price)

        val pro = EntitlementResolver.resolve(listOf(PurchaseSnapshot("callbridge_pro_monthly", "t", PurchaseState.PURCHASED, true, true, 0)))
        val cards = PlansPresenter.present(pro, mapOf("callbridge_pro_monthly" to "₹1,299"), null)
        assertEquals(
            listOf("Switch at renewal", "Current plan", "Switch", "Upgrade"),
            cards.map { it.actionLabel },
        )
        assertEquals("₹1,299", cards[1].price)
        assertEquals("Pro · renews automatically", PlansPresenter.entitlementLabel(pro))
    }

    @Test
    fun conversationTitlesUseFirstNamesButKeepUnsavedNumbersWhole() {
        val threads = SampleData.threads(SampleData.FIXED_NOW).associateBy { it.id }
        assertEquals("Daniel, Priya, Lucas", threads.getValue("t-launch").title)
        assertEquals("+1 (312) 555-0199", threads.getValue("t-pharmacy").title)
    }
}
