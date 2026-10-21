package net.evopoint.tidydial.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class CountryCatalogTest {

    private val catalog = CountryCatalog()

    @Test
    fun noDuplicateIsoCodes() {
        val codes = catalog.countries(Locale.ENGLISH).map { it.isoCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun everySupportedRegionAppears() {
        val codes = catalog.countries(Locale.ENGLISH).map { it.isoCode }.toSet()
        assertEquals(PhoneNumberUtil.getInstance().supportedRegions, codes)
    }

    @Test
    fun containsCommonCountriesWithCallingCodes() {
        val byIso = catalog.countries(Locale.ENGLISH).associateBy { it.isoCode }
        assertEquals(1, byIso.getValue("US").callingCode)
        assertEquals(44, byIso.getValue("GB").callingCode)
        assertEquals(49, byIso.getValue("DE").callingCode)
        assertEquals("+49", byIso.getValue("DE").callingCodeLabel)
    }

    @Test
    fun namesAreLocalized() {
        val english = catalog.country("DE", Locale.ENGLISH)!!.name
        val german = catalog.country("DE", Locale.GERMAN)!!.name
        assertEquals("Germany", english)
        assertEquals("Deutschland", german)
        assertNotEquals(english, german)
    }

    @Test
    fun sortedByLocalizedName() {
        for (locale in listOf(Locale.ENGLISH, Locale.GERMAN, Locale.FRENCH)) {
            val collator = Collator.getInstance(locale)
            val names = catalog.countries(locale).map { it.name }
            names.zipWithNext().forEach { (a, b) ->
                assertTrue("$a should sort before $b in $locale", collator.compare(a, b) <= 0)
            }
        }
    }

    @Test
    fun isSupported() {
        assertTrue(catalog.isSupported("US"))
        assertTrue(catalog.isSupported("de"))
        assertFalse(catalog.isSupported("ZZ"))
        assertFalse(catalog.isSupported("001"))
        assertFalse(catalog.isSupported(""))
        assertFalse(catalog.isSupported(null))
        assertNull(catalog.country("ZZ"))
    }

    @Test
    fun flagEmoji() {
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", catalog.country("US", Locale.ENGLISH)!!.flagEmoji)
    }
}
