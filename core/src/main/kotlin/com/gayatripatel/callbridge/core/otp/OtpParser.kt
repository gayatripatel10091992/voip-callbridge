package com.gayatripatel.callbridge.core.otp

/**
 * Pulls the one-time code out of an SMS delivered by the SMS Retriever API, e.g.
 * `<#> Your CallBridge code is 482913\nFA+9qCX9VSu`. The trailing 11-character app hash is ignored.
 */
object OtpParser {

    fun parse(message: String, length: Int = 6): String? {
        val code = Regex("(?<!\\d)\\d{$length}(?!\\d)")
        val afterKeyword = Regex("(?i)(code|otp|pin)\\b").find(message)
            ?.let { code.find(message, it.range.last + 1) }
        return (afterKeyword ?: code.find(message))?.value
    }
}
