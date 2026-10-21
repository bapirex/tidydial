package net.evopoint.tidydial.ui.options

import net.evopoint.tidydial.model.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountrySearchTest {

    private val countries = listOf(
        Country("AX", "Åland Islands", 358),
        Country("CA", "Canada", 1),
        Country("CI", "Côte d’Ivoire", 225),
        Country("DE", "Germany", 49),
        Country("DZ", "Algeria", 213),
        Country("GB", "United Kingdom", 44),
        Country("NE", "Niger", 227),
        Country("US", "United States", 1),
    )
    private val search = CountrySearch(countries)

    private fun isoCodes(query: String) = search.filter(query).map { it.isoCode }

    @Test
    fun blankQueryReturnsEverythingInOrder() {
        assertEquals(countries, search.filter("  "))
    }

    @Test
    fun nameMatchIgnoresCaseAndAccents() {
        assertEquals(listOf("CI"), isoCodes("COTE"))
        assertEquals(listOf("AX"), isoCodes("aland"))
    }

    @Test
    fun prefixMatchesRankBeforeSubstringMatches() {
        assertEquals(listOf("DE", "DZ", "NE"), isoCodes("ger"))
    }

    @Test
    fun wordPrefixRanksBeforeSubstring() {
        assertEquals(listOf("GB", "US"), isoCodes("united"))
        assertEquals("GB", isoCodes("kingdom").first())
    }

    @Test
    fun isoCodeMatchesFirst() {
        assertEquals("DE", isoCodes("de").first())
        assertEquals("GB", isoCodes("gb").first())
    }

    @Test
    fun callingCodeMatchesWithOrWithoutPlus() {
        assertEquals(listOf("GB"), isoCodes("+44"))
        assertEquals(listOf("CA", "US"), isoCodes("1"))
        assertEquals(listOf("CI", "NE"), isoCodes("+22"))
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(search.filter("Atlantis").isEmpty())
    }
}
