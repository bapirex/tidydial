package org.tidydial.model

import java.util.Locale

data class Country(
    val isoCode: String,
    val name: String,
    val callingCode: Int,
) {
    /** "+49" style label for the calling code. */
    val callingCodeLabel: String
        get() = "+$callingCode"

    val flagEmoji: String
        get() = flagEmojiOf(isoCode)

    companion object {
        private const val REGIONAL_INDICATOR_A = 0x1F1E6

        fun flagEmojiOf(isoCode: String): String {
            val iso = isoCode.uppercase(Locale.ROOT)
            if (iso.length != 2 || iso.any { it !in 'A'..'Z' }) return ""
            return buildString {
                iso.forEach { appendCodePoint(REGIONAL_INDICATOR_A + (it - 'A')) }
            }
        }
    }
}
