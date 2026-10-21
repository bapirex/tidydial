package net.evopoint.tidydial.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import net.evopoint.tidydial.model.Country
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Every region libphonenumber supports, with names localized at runtime. Thread-safe. */
class CountryCatalog(
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {
    private val regions: Set<String> by lazy { phoneUtil.supportedRegions.toSet() }
    private val cache = ConcurrentHashMap<Locale, List<Country>>()

    /** All supported countries sorted by their localized name. */
    fun countries(locale: Locale = Locale.getDefault()): List<Country> =
        cache.getOrPut(locale) { build(locale) }

    fun country(isoCode: String, locale: Locale = Locale.getDefault()): Country? {
        val iso = isoCode.uppercase(Locale.ROOT)
        if (iso !in regions) return null
        return Country(iso, displayName(iso, locale), phoneUtil.getCountryCodeForRegion(iso))
    }

    fun isSupported(isoCode: String?): Boolean =
        !isoCode.isNullOrBlank() && isoCode.trim().uppercase(Locale.ROOT) in regions

    fun displayName(isoCode: String, locale: Locale = Locale.getDefault()): String {
        val iso = isoCode.uppercase(Locale.ROOT)
        val name = try {
            Locale.Builder().setRegion(iso).build().getDisplayCountry(locale)
        } catch (e: IllegalArgumentException) {
            null
        }
        return if (name.isNullOrBlank()) iso else name
    }

    private fun build(locale: Locale): List<Country> {
        val collator = Collator.getInstance(locale)
        return regions
            .map { iso -> Country(iso, displayName(iso, locale), phoneUtil.getCountryCodeForRegion(iso)) }
            .sortedWith { a, b ->
                val byName = collator.compare(a.name, b.name)
                if (byName != 0) byName else a.isoCode.compareTo(b.isoCode)
            }
    }
}
