package net.evopoint.tidydial.data

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import java.io.IOException
import java.util.Locale

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Backup choices live in their own file, which is kept out of Android's cloud backup and device
 * transfer: a folder grant does not survive a reinstall, and a new install writes a new snapshot.
 */
private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup")

/**
 * @property asked the user answered the backup prompt (chose a folder or continued without).
 * @property enabled a backup is written before each update.
 * @property folderUri the tree URI of the backup folder.
 * @property folderName the folder's name as shown, so the UI never has to ask its provider.
 * @property snapshotFolderUri the folder the snapshot was last written to on this install. A
 *   folder that differs (the first backup, or a new folder) gets a snapshot of its own, so the
 *   current folder always holds one.
 */
data class BackupPrefs(
    val asked: Boolean = false,
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderName: String? = null,
    val snapshotFolderUri: String? = null,
)

/**
 * Persists [FormatConfig] and the [BackupPrefs]. Until the user picks a country, the country is
 * detected from the SIM, then the network, then the locale, falling back to [FALLBACK_COUNTRY].
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val catalog: CountryCatalog,
    private val backupDataStore: DataStore<Preferences> = dataStore,
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

    val backup: Flow<BackupPrefs> = backupDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            BackupPrefs(
                asked = prefs[KEY_BACKUP_ENABLED] != null,
                enabled = prefs[KEY_BACKUP_ENABLED] == true,
                folderUri = prefs[KEY_BACKUP_FOLDER_URI],
                folderName = prefs[KEY_BACKUP_FOLDER_NAME],
                snapshotFolderUri = prefs[KEY_BACKUP_SNAPSHOT_FOLDER_URI],
            )
        }
        .distinctUntilChanged()

    /** Remembers the backup folder and turns backups on. Throws [IOException] on failure. */
    suspend fun setBackupFolder(uri: String, name: String) {
        backupDataStore.edit { prefs ->
            prefs[KEY_BACKUP_FOLDER_URI] = uri
            prefs[KEY_BACKUP_FOLDER_NAME] = name
            prefs[KEY_BACKUP_ENABLED] = true
        }
    }

    /** Turns backups on or off, which also answers the prompt. Throws [IOException] on failure. */
    suspend fun setBackupEnabled(enabled: Boolean) {
        backupDataStore.edit { it[KEY_BACKUP_ENABLED] = enabled }
    }

    /** Remembers that [folderUri] holds a snapshot. Throws [IOException] on failure. */
    suspend fun markSnapshotWritten(folderUri: String) {
        backupDataStore.edit { it[KEY_BACKUP_SNAPSHOT_FOLDER_URI] = folderUri }
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

        /** Absent until the user answered the backup prompt. */
        private val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        private val KEY_BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        private val KEY_BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
        private val KEY_BACKUP_SNAPSHOT_FOLDER_URI = stringPreferencesKey("backup_snapshot_folder_uri")

        fun create(context: Context, catalog: CountryCatalog): SettingsRepository {
            val appContext = context.applicationContext
            return SettingsRepository(appContext.settingsDataStore, catalog, appContext.backupDataStore) {
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
