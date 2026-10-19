package org.tidydial

import android.app.Application
import android.content.Context
import org.tidydial.data.AndroidContactsRepository
import org.tidydial.data.ContactsDataSource
import org.tidydial.data.CountryCatalog
import org.tidydial.data.PhoneNumberFormatter
import org.tidydial.data.SettingsRepository
import org.tidydial.data.UndoJournal

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
}
