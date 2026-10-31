package net.evopoint.tidydial.data

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.MatchType
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.PhoneNumberUtil.ValidationResult
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber.CountryCodeSource
import com.google.i18n.phonenumbers.ShortNumberInfo
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import java.util.Locale

/**
 * Formats phone numbers with libphonenumber. Thread-safe.
 *
 * A number is only rewritten when libphonenumber considers it valid; anything else is returned
 * unchanged so that the app never "fixes" a number into something that dials differently.
 */
class PhoneNumberFormatter(
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
    private val shortNumberInfo: ShortNumberInfo = ShortNumberInfo.getInstance(),
) {

    data class FormattedResult(
        val formattedNumber: String,
        val isValid: Boolean,
        val isModified: Boolean,
    )

    fun format(rawNumber: String, config: FormatConfig): FormattedResult {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return unchanged(rawNumber, isValid = false)

        // USSD and service codes such as *#06# or #31#.
        if (trimmed.startsWith('*') || trimmed.startsWith('#')) {
            return unchanged(rawNumber, isValid = true)
        }

        val region = config.countryIso.uppercase(Locale.ROOT)
        val split = splitDialSequence(trimmed)
        if (split.main.isEmpty()) return unchanged(rawNumber, isValid = false)

        val parsed = try {
            // Keeps the raw input so that a Brazilian carrier code survives national formatting.
            phoneUtil.parseAndKeepRawInput(split.main, region)
        } catch (e: NumberParseException) {
            null
        }

        if (config.preserveShortNumbers &&
            !split.main.startsWith('+') &&
            isShortNumber(split.main, parsed, region)
        ) {
            return unchanged(rawNumber, isValid = true)
        }

        if (parsed == null || !isFormattable(parsed)) return unchanged(rawNumber, isValid = false)

        val base = formatBase(parsed, config, region)
        val result = if (split.delimiter == null) {
            base
        } else {
            base + separatorFor(config.extensionDelimiter, split.delimiter) + split.rest
        }

        return if (result == trimmed || differsOnlyInGrouping(trimmed, result)) {
            unchanged(rawNumber, isValid = true)
        } else {
            FormattedResult(result, isValid = true, isModified = true)
        }
    }

    /**
     * True when [original] already has the target's characters and is grouped with single
     * spaces, only in different places. Grouping conventions vary (libphonenumber writes a New
     * Zealand 0274 mobile as "+64 274 123 456" while people write "+64 27 412 3456"), so an
     * already grouped number is left alone rather than shuffled around. The country code must
     * still stand on its own, and formats without spaces (E.164) are always applied.
     */
    private fun differsOnlyInGrouping(original: String, formatted: String): Boolean {
        if (' ' !in formatted || ' ' !in original) return false
        if ("  " in original || original.any { it.isWhitespace() && it != ' ' }) return false
        if (original.replace(" ", "") != formatted.replace(" ", "")) return false
        return !formatted.startsWith('+') ||
            original.substringBefore(' ') == formatted.substringBefore(' ')
    }

    /**
     * The example mobile number of [regionIso] formatted in [style], for live previews.
     * Returns null for unknown regions.
     */
    fun exampleNumber(regionIso: String, style: NumberFormatStyle): String? {
        val region = regionIso.uppercase(Locale.ROOT)
        val example = phoneUtil.getExampleNumberForType(region, PhoneNumberType.MOBILE)
            ?: phoneUtil.getExampleNumber(region)
            ?: return null
        return phoneUtil.format(example, style.toLibraryFormat())
    }

    /**
     * Whether [a] and [b] are the same number, reading numbers without a country code as local to
     * [regionIso]. Formatting, extensions and dial sequences are ignored. Numbers libphonenumber
     * cannot parse only match when their digits are equal.
     */
    fun isSameNumber(a: String, b: String, regionIso: String): Boolean {
        val region = regionIso.uppercase(Locale.ROOT)
        val mainA = splitDialSequence(a.trim()).main
        val mainB = splitDialSequence(b.trim()).main
        val parsedA = parseWithoutExtension(mainA, region)
        val parsedB = parseWithoutExtension(mainB, region)
        if (parsedA != null && parsedB != null) {
            val match = phoneUtil.isNumberMatch(parsedA, parsedB)
            return match == MatchType.EXACT_MATCH || match == MatchType.NSN_MATCH
        }
        val digitsA = mainA.filter { it.isDigit() }
        return digitsA.isNotEmpty() && digitsA == mainB.filter { it.isDigit() }
    }

    private fun parseWithoutExtension(number: String, region: String): PhoneNumber? {
        if (number.isEmpty()) return null
        return try {
            phoneUtil.parse(number, region).clearExtension()
        } catch (e: NumberParseException) {
            null
        }
    }

    private fun isFormattable(number: PhoneNumber): Boolean =
        phoneUtil.isPossibleNumberWithReason(number) == ValidationResult.IS_POSSIBLE &&
            phoneUtil.isValidNumber(number)

    private fun isShortNumber(number: String, parsed: PhoneNumber?, region: String): Boolean {
        if (shortNumberInfo.isEmergencyNumber(number, region)) return true
        if (parsed == null) return false
        return shortNumberInfo.isPossibleShortNumberForRegion(parsed, region) &&
            !phoneUtil.isValidNumber(parsed)
    }

    private fun formatBase(number: PhoneNumber, config: FormatConfig, region: String): String {
        val inRegion = phoneUtil.getRegionCodeForNumber(number) == region
        val format = when {
            // Toll-free and shared-cost numbers such as Australia's 1800 only connect without
            // the country code, whatever the style.
            inRegion && !phoneUtil.canBeInternationallyDialled(number) -> PhoneNumberFormat.NATIONAL

            config.formatStyle == NumberFormatStyle.NATIONAL -> when {
                // A national format only makes sense inside the number's own region.
                !inRegion -> PhoneNumberFormat.INTERNATIONAL
                // Where calls only reliably connect with the country code, it is never removed.
                number.countryCodeSource != CountryCodeSource.FROM_DEFAULT_COUNTRY &&
                    !nationalFormatConnects(number) -> PhoneNumberFormat.INTERNATIONAL
                else -> PhoneNumberFormat.NATIONAL
            }

            else -> config.formatStyle.toLibraryFormat()
        }
        val formatted = if (format == PhoneNumberFormat.NATIONAL) {
            phoneUtil.formatNationalNumberWithPreferredCarrierCode(number, "")
        } else {
            phoneUtil.format(number, format)
        }

        // E.164 has no extension syntax; keep the extension as a dial sequence instead of dropping it.
        if (format == PhoneNumberFormat.E164 && number.hasExtension() && number.extension.isNotEmpty()) {
            return formatted + separatorFor(config.extensionDelimiter, WAIT) + number.extension
        }
        return formatted
    }

    /**
     * False where libphonenumber knows that a mobile has to dial the country code: Mexico, Chile
     * and Uzbekistan, and Brazil, where the national format needs a carrier code instead.
     */
    private fun nationalFormatConnects(number: PhoneNumber): Boolean {
        // North American numbers dial either way; libphonenumber merely prefers +1.
        if (number.countryCode == NANP_COUNTRY_CODE) return true
        val home = phoneUtil.getRegionCodeForCountryCode(number.countryCode)
        val dialled = phoneUtil.formatNumberForMobileDialing(number, home, false)
        return dialled.isNotEmpty() && !dialled.startsWith('+')
    }

    private fun separatorFor(delimiter: ExtensionDelimiter, original: String): String =
        when (delimiter) {
            ExtensionDelimiter.SEMICOLON -> WAIT
            ExtensionDelimiter.COMMA -> PAUSE
            ExtensionDelimiter.KEEP_ORIGINAL -> original
        }

    private fun splitDialSequence(number: String): DialSequence {
        val match = DIAL_DELIMITER.find(number) ?: return DialSequence(number, null, "")
        val rest = number.substring(match.range.last + 1).trim()
            .replace(PAUSE_OR_WAIT_BETWEEN_DIGITS) { normalizeDelimiter(it.value) }
        return DialSequence(
            main = number.substring(0, match.range.first).trim(),
            delimiter = normalizeDelimiter(match.value),
            rest = rest,
        )
    }

    /** [main] is the number to format; [delimiter] (already normalized) and [rest] follow it. */
    private class DialSequence(val main: String, val delimiter: String?, val rest: String)

    private fun unchanged(rawNumber: String, isValid: Boolean) =
        FormattedResult(rawNumber, isValid = isValid, isModified = false)

    private fun NumberFormatStyle.toLibraryFormat(): PhoneNumberFormat = when (this) {
        NumberFormatStyle.INTERNATIONAL -> PhoneNumberFormat.INTERNATIONAL
        NumberFormatStyle.NATIONAL -> PhoneNumberFormat.NATIONAL
        NumberFormatStyle.E164 -> PhoneNumberFormat.E164
        NumberFormatStyle.RFC3966 -> PhoneNumberFormat.RFC3966
    }

    private companion object {
        const val PAUSE = ","
        const val WAIT = ";"
        const val NANP_COUNTRY_CODE = 1

        /**
         * "," and ";" always start a dial sequence (except RFC 3966 parameters such as ";ext=").
         * "p"/"w" only count between digits, so vanity numbers like 1-800-FLOWERS stay intact.
         */
        val DIAL_DELIMITER = Regex(
            """,|;(?!(?:ext|phone-context|isub)=)|(?<=\d)[pw](?=[\d#*])""",
            RegexOption.IGNORE_CASE,
        )
        val PAUSE_OR_WAIT_BETWEEN_DIGITS = Regex("""(?<=\d)[pw](?=[\d#*])""", RegexOption.IGNORE_CASE)

        fun normalizeDelimiter(delimiter: String): String = when (delimiter) {
            "p", "P" -> PAUSE
            "w", "W" -> WAIT
            else -> delimiter
        }
    }
}
