package com.gayatripatel.callbridge.core.screening

import com.gayatripatel.callbridge.core.model.CallSettings

enum class Verdict { RING, SEND_TO_VOICEMAIL, BLOCK }

data class ScreeningDecision(val verdict: Verdict, val label: String?)

/**
 * Decides what to do with an incoming call before the phone rings.
 * Order matters: the user's own block list and contacts always beat the spam score.
 */
object CallScreener {

    fun decide(
        e164: String,
        isContact: Boolean,
        spamScore: Int?,
        settings: CallSettings,
    ): ScreeningDecision {
        if (settings.blockedNumbers.any { matches(it, e164) }) return ScreeningDecision(Verdict.BLOCK, "Blocked")
        if (isContact) return ScreeningDecision(Verdict.RING, null)
        if (spamScore != null && spamScore >= 95) return ScreeningDecision(Verdict.BLOCK, "Spam")
        if (spamScore != null && spamScore >= settings.spamThreshold) {
            return ScreeningDecision(Verdict.SEND_TO_VOICEMAIL, "Likely spam")
        }
        if (settings.screenUnknownCallers) return ScreeningDecision(Verdict.SEND_TO_VOICEMAIL, "Unknown caller")
        return ScreeningDecision(Verdict.RING, null)
    }

    /** Block-list entries are exact E.164 numbers or a prefix ending in `*`, e.g. `+1900*`. */
    fun matches(pattern: String, e164: String): Boolean =
        if (pattern.endsWith("*")) e164.startsWith(pattern.dropLast(1)) else pattern == e164
}
