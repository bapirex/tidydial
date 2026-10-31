package net.evopoint.tidydial.data

import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberFormatterTest {

    private val formatter = PhoneNumberFormatter()

    private fun us(
        style: NumberFormatStyle = NumberFormatStyle.INTERNATIONAL,
        delimiter: ExtensionDelimiter = ExtensionDelimiter.SEMICOLON,
        preserveShortNumbers: Boolean = true,
    ) = FormatConfig(
        countryIso = "US",
        formatStyle = style,
        extensionDelimiter = delimiter,
        preserveShortNumbers = preserveShortNumbers,
    )

    private fun assertUnchanged(input: String, config: FormatConfig, expectValid: Boolean) {
        val result = formatter.format(input, config)
        assertEquals(input, result.formattedNumber)
        assertFalse(result.isModified)
        assertEquals(expectValid, result.isValid)
    }

    // Existing behaviour

    @Test
    fun nationalFormattingUs() {
        val result = formatter.format("2125550199", us(NumberFormatStyle.NATIONAL))
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("(212) 555-0199", result.formattedNumber)
    }

    @Test
    fun internationalFormattingUs() {
        val result = formatter.format("2125550199", us())
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("+1 212-555-0199", result.formattedNumber)
    }

    @Test
    fun e164Formatting() {
        val result = formatter.format("(212) 555-0199", us(NumberFormatStyle.E164))
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("+12125550199", result.formattedNumber)
    }

    @Test
    fun rfc3966Formatting() {
        val result = formatter.format("212-555-0199", us(NumberFormatStyle.RFC3966))
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("tel:+1-212-555-0199", result.formattedNumber)
    }

    @Test
    fun internationalNumberFromDifferentCountry() {
        val result = formatter.format("+442079460958", us())
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("+44 20 7946 0958", result.formattedNumber)
    }

    @Test
    fun alreadyFormattedNumberIsNotModified() {
        assertUnchanged("(212) 555-0199", us(NumberFormatStyle.NATIONAL), expectValid = true)
    }

    @Test
    fun preserveShortNumbers() {
        assertUnchanged("911", us(), expectValid = true)
        assertUnchanged("838255", us(), expectValid = true)
    }

    @Test
    fun extensionWithCommaBecomesWait() {
        val result = formatter.format(
            "2125550199,456",
            us(NumberFormatStyle.NATIONAL, ExtensionDelimiter.SEMICOLON),
        )
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("(212) 555-0199;456", result.formattedNumber)
    }

    @Test
    fun extensionWithPBecomesPause() {
        val result = formatter.format(
            "2125550199p456",
            us(NumberFormatStyle.NATIONAL, ExtensionDelimiter.COMMA),
        )
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals("(212) 555-0199,456", result.formattedNumber)
    }

    @Test
    fun ussdCodeIsPreserved() {
        assertUnchanged("*#06#", us(), expectValid = true)
    }

    @Test
    fun textIsNotANumber() {
        assertUnchanged("abcdef", us(), expectValid = false)
    }

    @Test
    fun blankInput() {
        val result = formatter.format("   ", us())
        assertFalse(result.isValid)
        assertFalse(result.isModified)
    }

    // NATIONAL style never drops a foreign country code

    @Test
    fun nationalStyleKeepsForeignCountryCode() {
        val result = formatter.format("+442079460958", us(NumberFormatStyle.NATIONAL))
        assertTrue(result.isValid)
        assertEquals("+44 20 7946 0958", result.formattedNumber)
    }

    @Test
    fun nationalStyleKeepsCountryCodeOfOtherRegionSharingCallingCode() {
        val result = formatter.format("+1 416 555 0199", us(NumberFormatStyle.NATIONAL))
        assertEquals("+1 416-555-0199", result.formattedNumber)
    }

    @Test
    fun nationalStyleStripsOwnCountryCode() {
        val result = formatter.format("+1 212 555 0199", us(NumberFormatStyle.NATIONAL))
        assertEquals("(212) 555-0199", result.formattedNumber)
    }

    // Letters, extensions and dial sequences

    @Test
    fun vanityNumberIsNotSplitAtW() {
        val result = formatter.format("1-800-FLOWERS", us())
        assertTrue(result.isValid)
        assertEquals("+1 800-356-9377", result.formattedNumber)
        assertFalse(result.formattedNumber.contains(';'))
        assertFalse(result.formattedNumber.contains(','))
    }

    @Test
    fun xExtensionIsHandledByLibphonenumber() {
        val result = formatter.format("2125550199 x123", us())
        assertTrue(result.isValid)
        assertEquals("+1 212-555-0199 ext. 123", result.formattedNumber)
    }

    @Test
    fun extDotExtensionIsHandledByLibphonenumber() {
        val result = formatter.format("212-555-0199 ext. 123", us(NumberFormatStyle.NATIONAL))
        assertEquals("(212) 555-0199 ext. 123", result.formattedNumber)
    }

    @Test
    fun rfc3966ExtensionParameterIsNotTreatedAsWait() {
        val result = formatter.format("tel:+1-212-555-0199;ext=123", us(NumberFormatStyle.NATIONAL))
        assertEquals("(212) 555-0199 ext. 123", result.formattedNumber)
    }

    @Test
    fun e164KeepsExtensionAsDialSequence() {
        val result = formatter.format("2125550199 x123", us(NumberFormatStyle.E164))
        assertEquals("+12125550199;123", result.formattedNumber)
    }

    @Test
    fun pBetweenDigitsIsNormalizedToComma() {
        val result = formatter.format("2125550199p123", us(delimiter = ExtensionDelimiter.KEEP_ORIGINAL))
        assertEquals("+1 212-555-0199,123", result.formattedNumber)
    }

    @Test
    fun wBetweenDigitsIsNormalizedToSemicolon() {
        val result = formatter.format("2125550199W123", us(delimiter = ExtensionDelimiter.KEEP_ORIGINAL))
        assertEquals("+1 212-555-0199;123", result.formattedNumber)
    }

    @Test
    fun laterDelimitersAreNormalizedButKept() {
        val result = formatter.format("2125550199,123p456", us(delimiter = ExtensionDelimiter.SEMICOLON))
        assertEquals("+1 212-555-0199;123,456", result.formattedNumber)
    }

    // Short and emergency numbers

    @Test
    fun germanEmergencyNumberIsPreserved() {
        val config = FormatConfig(countryIso = "DE", preserveShortNumbers = true)
        assertUnchanged("112", config, expectValid = true)
        assertUnchanged("110", config, expectValid = true)
    }

    @Test
    fun usEmergencyNumberIsPreserved() {
        assertUnchanged("911", us(NumberFormatStyle.E164), expectValid = true)
    }

    @Test
    fun shortNumbersAreStillNotRewrittenWhenPreservationIsOff() {
        assertUnchanged("911", us(preserveShortNumbers = false), expectValid = false)
        assertUnchanged(
            "112",
            FormatConfig(countryIso = "DE", preserveShortNumbers = false),
            expectValid = false,
        )
    }

    // Invalid numbers are never rewritten

    @Test
    fun impossibleNumberIsNotModified() {
        assertUnchanged("21255501", us(), expectValid = false)
    }

    @Test
    fun localOnlyNumberIsNotGivenACountryCode() {
        assertUnchanged("5550199", us(), expectValid = false)
    }

    @Test
    fun possibleButInvalidNumberIsNotModified() {
        assertUnchanged("+1 555 555 5555", us(NumberFormatStyle.E164), expectValid = false)
    }

    @Test
    fun surroundingWhitespaceIsPreservedWhenUnchanged() {
        val result = formatter.format(" (212) 555-0199 ", us(NumberFormatStyle.NATIONAL))
        assertFalse(result.isModified)
        assertEquals(" (212) 555-0199 ", result.formattedNumber)
    }

    // Example numbers for previews

    @Test
    fun exampleNumberInEachStyle() {
        val international = formatter.exampleNumber("DE", NumberFormatStyle.INTERNATIONAL)
        val national = formatter.exampleNumber("de", NumberFormatStyle.NATIONAL)
        val e164 = formatter.exampleNumber("DE", NumberFormatStyle.E164)
        val rfc = formatter.exampleNumber("DE", NumberFormatStyle.RFC3966)

        assertNotNull(international)
        assertTrue(international!!.startsWith("+49 "))
        assertNotNull(national)
        assertTrue(national!!.startsWith("0"))
        assertNotNull(e164)
        assertTrue(e164!!.matches(Regex("""\+49\d+""")))
        assertNotNull(rfc)
        assertTrue(rfc!!.startsWith("tel:+49-"))
    }

    @Test
    fun exampleNumberRoundTripsThroughFormatter() {
        val config = us(NumberFormatStyle.NATIONAL)
        val example = formatter.exampleNumber("US", NumberFormatStyle.NATIONAL)!!
        assertUnchanged(example, config, expectValid = true)
    }

    // Grouping: an already grouped number is not reshuffled (reported by a tester for a New Zealand 027 4 mobile)

    private fun nz(style: NumberFormatStyle = NumberFormatStyle.INTERNATIONAL) =
        FormatConfig(countryIso = "NZ", formatStyle = style)

    private fun assertFormatted(input: String, config: FormatConfig, expected: String) {
        val result = formatter.format(input, config)
        assertTrue(result.isValid)
        assertTrue(result.isModified)
        assertEquals(expected, result.formattedNumber)
    }

    @Test
    fun groupedInternationalNumberWithOtherSpacingIsKept() {
        // libphonenumber groups 0274 mobiles as "+64 274 123 456".
        assertUnchanged("+64 27 412 3456", nz(), expectValid = true)
        assertUnchanged("+64 27 412 3456", us(), expectValid = true)
    }

    @Test
    fun groupedNationalNumberWithOtherSpacingIsKept() {
        assertUnchanged("027 412 3456", nz(NumberFormatStyle.NATIONAL), expectValid = true)
    }

    @Test
    fun ungroupedNumberIsStillGrouped() {
        assertFormatted("+64211234567", nz(), "+64 21 123 4567")
        assertFormatted("+64274123456", nz(), "+64 274 123 456")
    }

    @Test
    fun otherSeparatorsAreStillNormalized() {
        assertFormatted("+64 3-234 5678", nz(), "+64 3 234 5678")
        assertFormatted("+1 212 555 0199", us(), "+1 212-555-0199")
    }

    @Test
    fun countryCodeMustStandOnItsOwn() {
        assertFormatted("+6427 412 3456", nz(), "+64 274 123 456")
    }

    @Test
    fun irregularWhitespaceIsNormalized() {
        assertFormatted("+64  27 412 3456", nz(), "+64 274 123 456")
    }

    @Test
    fun formatsWithoutSpacesStillApply() {
        assertFormatted("+64 27 412 3456", nz(NumberFormatStyle.E164), "+64274123456")
        assertFormatted("+64 27 412 3456", nz(NumberFormatStyle.RFC3966), "tel:+64-274-123-456")
    }

    // Country rules: a number must still connect the way it is written

    private fun config(region: String, style: NumberFormatStyle) =
        FormatConfig(countryIso = region, formatStyle = style)

    @Test
    fun domesticOnlyNumbersNeverGetACountryCode() {
        for (style in NumberFormatStyle.entries) {
            assertUnchanged("1800 123 456", config("AU", style), expectValid = true)
            assertUnchanged("0800 12 3456", config("BR", style), expectValid = true)
            assertUnchanged("400 123 4567", config("CN", style), expectValid = true)
        }
    }

    @Test
    fun domesticOnlyNumberLosesAnUndiallableCountryCode() {
        assertFormatted("+61 1800 123 456", config("AU", NumberFormatStyle.E164), "1800 123 456")
    }

    @Test
    fun nationalStyleKeepsCountryCodeWhereMobilesNeedIt() {
        assertUnchanged("+55 11 2345-6789", config("BR", NumberFormatStyle.NATIONAL), expectValid = true)
        assertUnchanged("+52 222 123 4567", config("MX", NumberFormatStyle.NATIONAL), expectValid = true)
        assertFormatted("+522221234567", config("MX", NumberFormatStyle.NATIONAL), "+52 222 123 4567")
    }

    @Test
    fun nationalStyleStillFormatsNationalNumbersWhereMobilesNeedTheCountryCode() {
        assertFormatted("11 2345 6789", config("BR", NumberFormatStyle.NATIONAL), "(11) 2345-6789")
    }

    @Test
    fun brazilianCarrierCodeIsKept() {
        assertFormatted("021 11 2345-6789", config("BR", NumberFormatStyle.NATIONAL), "0 21 (11) 2345-6789")
        assertFormatted("021 11 2345-6789", config("BR", NumberFormatStyle.INTERNATIONAL), "+55 11 2345-6789")
    }

    @Test
    fun nationalStyleStripsCountryCodeOfRegionSharingCallingCode() {
        assertFormatted("+44 7797 712345", config("JE", NumberFormatStyle.NATIONAL), "07797 712345")
    }

    @Test
    fun trunkPrefixInParenthesesIsDropped() {
        assertFormatted("+44 (0)20 7946 0958", config("GB", NumberFormatStyle.INTERNATIONAL), "+44 20 7946 0958")
    }

    @Test
    fun exampleNumberForUnknownRegionIsNull() {
        assertNull(formatter.exampleNumber("ZZ", NumberFormatStyle.INTERNATIONAL))
    }

    @Test
    fun sameNumberIgnoresFormatting() {
        assertTrue(formatter.isSameNumber("2125550123", "+1 212-555-0123", "US"))
        assertTrue(formatter.isSameNumber("(212) 555-0123", "tel:+1-212-555-0123", "US"))
        assertTrue(formatter.isSameNumber("+12125550123", "+1 212 555 0123", "DE"))
        assertTrue(formatter.isSameNumber("021 123 4567", "+64 21 123 4567", "NZ"))
    }

    @Test
    fun sameNumberIgnoresExtensionsAndDialSequences() {
        assertTrue(formatter.isSameNumber("3105550142,12", "+1 310-555-0142", "US"))
        assertTrue(formatter.isSameNumber("+1 310-555-0142 ext. 12", "+13105550142", "US"))
        assertTrue(formatter.isSameNumber("+1 310-555-0142;99", "3105550142,12", "US"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(formatter.isSameNumber("+1 212-555-0123", "+1 212-555-0124", "US"))
        // Same digits, read in different countries.
        assertFalse(formatter.isSameNumber("+64 21 123 4567", "+61 21 123 4567", "US"))
        // A local number is read in the configured country only.
        assertFalse(formatter.isSameNumber("021 123 4567", "+64 21 123 4567", "US"))
    }

    @Test
    fun unparsableNumbersMatchOnlyByDigits() {
        assertTrue(formatter.isSameNumber("*#06#", "*#06#", "US"))
        assertFalse(formatter.isSameNumber("*#06#", "*#07#", "US"))
        assertFalse(formatter.isSameNumber("", "", "US"))
    }

    @Test
    fun sameDialledNumberAllowsOtherDelimitersForTheSameDigits() {
        assertTrue(formatter.isSameDialledNumber("3105550142,12", "+1 310-555-0142;12", "US"))
        assertTrue(formatter.isSameDialledNumber("3105550142p12", "+1 310-555-0142,12", "US"))
        assertTrue(formatter.isSameDialledNumber("+1 310-555-0142 ext. 12", "+13105550142;12", "US"))
        assertTrue(formatter.isSameDialledNumber("2125550101", "+1 212-555-0101", "us"))
    }

    @Test
    fun sameDialledNumberNeedsTheSameExtension() {
        assertFalse(formatter.isSameDialledNumber("+1 310-555-0142;12", "+1 310-555-0142;34", "US"))
        assertFalse(formatter.isSameDialledNumber("+1 310-555-0142;12", "+1 310-555-0142", "US"))
        assertFalse(formatter.isSameDialledNumber("+1 310-555-0142 ext. 12", "+1 310-555-0142", "US"))
        assertFalse(formatter.isSameDialledNumber("+1 212-555-0101", "+1 212-555-0102", "US"))
    }

    @Test
    fun sameDialledNumberCompareUnparsableNumbersByDigits() {
        assertTrue(formatter.isSameDialledNumber("*#06#", "*#06#", "US"))
        assertFalse(formatter.isSameDialledNumber("555-01,1", "555-01,2", "US"))
        assertTrue(formatter.isSameDialledNumber("555-01;1", "555 01,1", "US"))
    }
}
