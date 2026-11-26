package com.gayatripatel.callbridge.core.link

import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import java.net.URI
import java.net.URLDecoder

sealed interface Destination {
    data class Dial(val e164: String) : Destination
    data class Conversation(val threadId: String) : Destination
    data object Voicemail : Destination
    data class Plans(val highlightProductId: String?) : Destination
}

/** App links (`https://callbridge.example.com/...`) and the custom `callbridge://` scheme. */
object DeepLinkParser {
    const val HOST = "callbridge.example.com"

    fun parse(url: String?): Destination? {
        val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        val query = queryParams(uri)
        val segments = when (uri.scheme) {
            "callbridge" -> listOfNotNull(uri.host) + pathSegments(uri)
            "https" -> if (uri.host == HOST) pathSegments(uri) else return null
            else -> return null
        }
        return when (segments.firstOrNull()) {
            "dial", "call" -> (segments.getOrNull(1) ?: query["number"])
                ?.let(PhoneNumbers::toE164)?.let(Destination::Dial)
            "messages" -> segments.getOrNull(1)?.takeIf(::isSafeId)?.let(Destination::Conversation)
            "voicemail" -> Destination.Voicemail
            "plans" -> Destination.Plans(query["plan"])
            else -> null
        }
    }

    private fun isSafeId(id: String) = id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

sealed interface HandoffResult {
    data object NotHandoff : HandoffResult
    data class Invalid(val reason: String) : HandoffResult
    data class Valid(val sessionToken: String, val productId: String, val email: String?) : HandoffResult
}

/**
 * The web signup funnel runs in a WebView. When the user finishes the web steps the page navigates to
 * `https://callbridge.example.com/app/handoff?session=…&plan=…`; the app intercepts that URL and continues
 * natively with Google Play Billing. Only HTTPS on our host is trusted.
 */
object HandoffParser {
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{16,128}$")

    fun parse(url: String, knownProducts: Collection<String>): HandoffResult {
        val uri = runCatching { URI(url) }.getOrNull() ?: return HandoffResult.NotHandoff
        if (uri.host != DeepLinkParser.HOST || pathSegments(uri) != listOf("app", "handoff")) return HandoffResult.NotHandoff
        if (uri.scheme != "https") return HandoffResult.Invalid("insecure scheme")
        val q = queryParams(uri)
        val session = q["session"] ?: return HandoffResult.Invalid("missing session")
        if (!tokenPattern.matches(session)) return HandoffResult.Invalid("malformed session")
        val plan = q["plan"] ?: return HandoffResult.Invalid("missing plan")
        if (plan !in knownProducts) return HandoffResult.Invalid("unknown plan")
        return HandoffResult.Valid(session, plan, q["email"]?.takeIf { '@' in it })
    }
}

internal fun pathSegments(uri: URI): List<String> = (uri.path ?: "").split('/').filter { it.isNotEmpty() }

internal fun queryParams(uri: URI): Map<String, String> = (uri.rawQuery ?: "")
    .split('&')
    .filter { '=' in it }
    .associate {
        val (k, v) = it.split('=', limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
