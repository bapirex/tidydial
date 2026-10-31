package net.evopoint.tidydial

import android.app.Application
import android.content.Context
import net.evopoint.tidydial.data.AndroidContactsRepository
import net.evopoint.tidydial.data.BackupManager
import net.evopoint.tidydial.data.BackupStore
import net.evopoint.tidydial.data.ContactsDataSource
import net.evopoint.tidydial.data.CountryCatalog
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.data.SafBackupStore
import net.evopoint.tidydial.data.SettingsRepository
import net.evopoint.tidydial.data.UndoJournal

class App : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Manual dependency container; everything is created on first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val formatter: PhoneNumberFormatter by lazy { PhoneNumberFormatter() }
    val countryCatalog: CountryCatalog by lazy { CountryCatalog() }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository.create(appContext, countryCatalog)
    }
    val contactsRepository: ContactsDataSource by lazy {
        AndroidContactsRepository(appContext, formatter)
    }
    // noBackupFilesDir is never backed up or moved by device-to-device transfer on any API level.
    val undoJournal: UndoJournal by lazy { UndoJournal(appContext.noBackupFilesDir) }
    val backupStore: BackupStore by lazy { SafBackupStore(appContext) }
    val backupManager: BackupManager by lazy {
        BackupManager(backupStore, settingsRepository, contactsRepository, BuildConfig.VERSION_NAME, formatter)
    }
}
