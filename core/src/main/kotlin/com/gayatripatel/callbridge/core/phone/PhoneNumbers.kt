package com.gayatripatel.callbridge.core.phone

/**
 * North American Numbering Plan helpers used by the dialer and contact rows.
 * Small and dependency-free on purpose: the dialer formats on every key press.
 */
object PhoneNumbers {

    /** Formats digits as the user types: `4155550` -> `(415) 555-0`, `+14155550134` -> `+1 (415) 555-0134`. */
    fun formatAsYouType(raw: String): String {
        val plus = raw.trimStart().startsWith("+")
        val digits = raw.filter(Char::isDigit)
        if (digits.isEmpty()) return if (plus) "+" else ""
        // International numbers outside +1 are left ungrouped: grouping rules differ per country.
        if (plus && !digits.startsWith("1")) return "+$digits"

        val hasTrunkPrefix = digits.startsWith("1")
        val national = if (hasTrunkPrefix) digits.drop(1) else digits
        if (national.length > 10) return (if (plus) "+" else "") + digits

        val prefix = when {
            !hasTrunkPrefix -> ""
            plus -> "+1 "
            else -> "1 "
        }
        return (prefix + groupNational(national)).trim()
    }

    /** Normalises user input to E.164, or returns null when it can't be dialled. */
    fun toE164(raw: String): String? {
        val plus = raw.trimStart().startsWith("+")
        val digits = raw.filter(Char::isDigit)
        return when {
            plus -> if (digits.length in 8..15) "+$digits" else null
            digits.length == 10 && digits[0] in '2'..'9' -> "+1$digits"
            digits.length == 11 && digits[0] == '1' && digits[1] in '2'..'9' -> "+$digits"
            else -> null
        }
    }

    /** `+14155550134` -> `(415) 555-0134`; anything else is returned unchanged. */
    fun display(e164: String): String =
        if (e164.startsWith("+1") && e164.length == 12) groupNational(e164.drop(2)) else e164

    private fun groupNational(n: String): String = when {
        n.length <= 3 -> n
        n.length <= 6 -> "(${n.take(3)}) ${n.drop(3)}"
        else -> "(${n.take(3)}) ${n.substring(3, 6)}-${n.drop(6)}"
    }
}
