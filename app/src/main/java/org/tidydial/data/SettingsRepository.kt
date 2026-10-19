package org.tidydial.data

import android.content.Context
import android.telephony.TelephonyManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.tidydial.model.ExtensionDelimiter
import org.tidydial.model.FormatConfig
import org.tidydial.model.NumberFormatStyle

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists [FormatConfig]. Until the user picks a country, the country is detected from the SIM,
 * then the network, then the locale, falling back to [FALLBACK_COUNTRY].
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val catalog: CountryCatalog,
    countryCandidates: () -> List<String?>,
) {
    /** The auto-detected country; the UI can mark the config's country as "auto-detected" when equal. */
    val detectedCountryIso: String by lazy {
        countryCandidates()
            .firstNotNullOfOrNull { candidate ->
                candidate?.trim()?.uppercase(Locale.ROOT)?.takeIf { catalog.isSupported(it) }
            }
            ?: FALLBACK_COUNTRY
    }

    val config: Flow<FormatConfig> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toConfig() }
        .distinctUntilChanged()

    suspend fun update(config: FormatConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_COUNTRY] = config.countryIso.uppercase(Locale.ROOT)
            prefs[KEY_STYLE] = config.formatStyle.name
            prefs[KEY_DELIMITER] = config.extensionDelimiter.name
            prefs[KEY_PRESERVE_SHORT] = config.preserveShortNumbers
            prefs[KEY_EXCLUDED_ACCOUNTS] = config.excludedAccountTypes
        }
    }

    private fun Preferences.toConfig(): FormatConfig {
        val defaults = FormatConfig()
        return FormatConfig(
            countryIso = this[KEY_COUNTRY]
                ?.takeIf { catalog.isSupported(it) }
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?: detectedCountryIso,
            formatStyle = this[KEY_STYLE]
                ?.let { name -> NumberFormatStyle.entries.firstOrNull { it.name == name } }
                ?: defaults.formatStyle,
            extensionDelimiter = this[KEY_DELIMITER]
                ?.let { name -> ExtensionDelimiter.entries.firstOrNull { it.name == name } }
                ?: defaults.extensionDelimiter,
            preserveShortNumbers = this[KEY_PRESERVE_SHORT] ?: defaults.preserveShortNumbers,
            excludedAccountTypes = this[KEY_EXCLUDED_ACCOUNTS] ?: defaults.excludedAccountTypes,
        )
    }

    companion object {
        const val FALLBACK_COUNTRY = "US"

        private val KEY_COUNTRY = stringPreferencesKey("country_iso")
        private val KEY_STYLE = stringPreferencesKey("format_style")
        private val KEY_DELIMITER = stringPreferencesKey("extension_delimiter")
        private val KEY_PRESERVE_SHORT = booleanPreferencesKey("preserve_short_numbers")
        private val KEY_EXCLUDED_ACCOUNTS = stringSetPreferencesKey("excluded_account_types")

        fun create(context: Context, catalog: CountryCatalog): SettingsRepository {
            val appContext = context.applicationContext
            return SettingsRepository(appContext.settingsDataStore, catalog) {
                val telephony = appContext.getSystemService(TelephonyManager::class.java)
                listOf(
                    runCatching { telephony?.simCountryIso }.getOrNull(),
                    runCatching { telephony?.networkCountryIso }.getOrNull(),
                    Locale.getDefault().country,
                )
            }
        }
    }
}
