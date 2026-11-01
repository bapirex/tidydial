package net.evopoint.tidydial.data

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import net.evopoint.tidydial.model.AppChat
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.model.PhoneEntry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * [AndroidContactsRepository.callOptions] against a fake contacts provider and package manager.
 * The contact and its numbers are fictional (US 555-01xx); the packages are made up.
 */
@RunWith(AndroidJUnit4::class)
class ContactsRepositoryCallOptionsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeContactsProvider
    private val repository by lazy {
        AndroidContactsRepository(app, PhoneNumberFormatter(), ioDispatcher = Dispatchers.Unconfined)
    }

    /** Dana's mobile in Google, as the list shows it. */
    private val mobile = entry(original = "6465550144", formatted = "+1 646-555-0144")

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        provider = Robolectric.buildContentProvider(FakeContactsProvider::class.java)
            .create(ContactsContract.AUTHORITY)
            .get()
        installDialer()
    }

    @Test
    fun offersMatchingAppsAfterThePhoneAppByNameVoiceFirst() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            phone(2, raw = 140, "+1 212-555-0190", "com.google"),
            // WhatsApp: its own copy of the number, voice and video, and a duplicate voice row.
            phone(3, raw = 141, "+1 646-555-0144", "com.whatsapp"),
            call(4, raw = 141, WA_VIDEO, "16465550144@s.whatsapp.net", "com.whatsapp"),
            call(5, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp"),
            call(6, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp"),
            // Free Viber call for this number; Viber Out (paid) is not a known call row.
            call(7, raw = 144, VIBER_VOICE, "+16465550144", "com.viber.voip"),
            call(8, raw = 144, "vnd.com.viber.voip.viber_out_call_none_viber", "+16465550144", "com.viber.voip"),
            // Signal for the other number.
            call(9, raw = 142, SIGNAL_VOICE, "+1 212-555-0190", "org.thoughtcrime.securesms"),
        )
        handle(5, WA_VOICE, "org.example.whatsapp", label = "WhatsApp")
        handle(4, WA_VIDEO, "org.example.whatsapp", label = "WhatsApp")
        handle(7, VIBER_VOICE, "org.example.viber", label = "Viber")
        handle(9, SIGNAL_VOICE, "org.example.signal", label = "Signal")

        val options = repository.callOptions(mobile, "US")

        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Viber", false, "org.example.viber", 7, VIBER_VOICE),
                app("WhatsApp", false, "org.example.whatsapp", 5, WA_VOICE),
                app("WhatsApp", true, "org.example.whatsapp", 4, WA_VIDEO),
            ),
            options,
        )
        // Skipped accounts are read too: the query is not limited to any account.
        assertEquals(listOf("14"), provider.contactIds)
    }

    @Test
    fun callsGuardedByAnotherPermissionOrWithoutAnAppAreLeftOut() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            call(2, raw = 145, MEET_AUDIO, "6465550144", "com.google.android.apps.tachyon"),
            call(3, raw = 145, MEET_VIDEO, "6465550144", "com.google.android.apps.tachyon"),
            call(4, raw = 146, "vnd.ch.threema.app.call", "ABCD1234", "ch.threema.app"),
            call(5, raw = 147, TELEGRAM_VOICE, "123456789", "org.telegram.messenger", "Voice call +1 646 555 0144"),
            call(6, raw = 148, VIBER_VOICE, "+16465550144", "com.viber.voip"),
        )
        handle(2, MEET_AUDIO, "org.example.meet", label = "Meet", permission = Manifest.permission.RECORD_AUDIO)
        handle(3, MEET_VIDEO, "org.example.meet", label = "Meet", exported = false)
        // Threema has no app label here, so the account name is used.
        handle(4, "vnd.ch.threema.app.call", "org.example.threema", label = null)
        handle(6, VIBER_VOICE, "org.example.viber", label = "Viber", permission = "org.example.viber.permission.CALL")

        val options = repository.callOptions(mobile, "US")

        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Threema", false, "org.example.threema", 4, "vnd.ch.threema.app.call"),
            ),
            options,
        )
    }

    @Test
    fun callsGuardedByTheCallPermissionAreOfferedWithTheAppsChat() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            phone(2, raw = 141, "+1 646-555-0144", "com.whatsapp"),
            call(3, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp"),
            call(4, raw = 141, WA_VIDEO, "16465550144@s.whatsapp.net", "com.whatsapp"),
            // A chat for another number of the contact, then the one for this number.
            chat(5, raw = 141, WA_CHAT, "12125550190@s.whatsapp.net", "com.whatsapp"),
            chat(6, raw = 141, WA_CHAT, "16465550144@s.whatsapp.net", "com.whatsapp"),
            // Telegram keeps the number in the summary only.
            call(7, raw = 147, TELEGRAM_VOICE, "123456789", "org.telegram.messenger", "Voice call +1 646 555 0144"),
            chat(8, raw = 147, TELEGRAM_CHAT, "123456789", "org.telegram.messenger", "Message +1 646 555 0144"),
            // Google Meet adds no chat.
            call(9, raw = 145, MEET_AUDIO, "6465550144", "com.google.android.apps.tachyon"),
        )
        handle(3, WA_VOICE, "org.example.whatsapp", label = "WhatsApp", permission = Manifest.permission.CALL_PHONE)
        handle(4, WA_VIDEO, "org.example.whatsapp", label = "WhatsApp", permission = Manifest.permission.CALL_PHONE)
        handle(5, WA_CHAT, "org.example.whatsapp", label = "WhatsApp", activity = CHAT_ACTIVITY)
        handle(6, WA_CHAT, "org.example.whatsapp", label = "WhatsApp", activity = CHAT_ACTIVITY)
        handle(7, TELEGRAM_VOICE, "org.example.telegram", label = "Telegram", permission = Manifest.permission.CALL_PHONE)
        handle(8, TELEGRAM_CHAT, "org.example.telegram", label = "Telegram", activity = CHAT_ACTIVITY)
        handle(9, MEET_AUDIO, "org.example.meet", label = "Meet", permission = Manifest.permission.CALL_PHONE)

        val options = repository.callOptions(mobile, "US")

        val whatsAppChat = AppChat("org.example.whatsapp", "org.example.whatsapp.ChatActivity", 6, ITEM + WA_CHAT)
        val telegramChat = AppChat("org.example.telegram", "org.example.telegram.ChatActivity", 8, ITEM + TELEGRAM_CHAT)
        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Meet", false, "org.example.meet", 9, MEET_AUDIO).guarded(chat = null),
                app("Telegram", false, "org.example.telegram", 7, TELEGRAM_VOICE).guarded(telegramChat),
                app("WhatsApp", false, "org.example.whatsapp", 3, WA_VOICE).guarded(whatsAppChat),
                app("WhatsApp", true, "org.example.whatsapp", 4, WA_VIDEO).guarded(whatsAppChat),
            ),
            options,
        )
    }

    @Test
    fun aChatIsOnlyUsedWhenItsOwnAppOpensItWithoutThePermission() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            call(2, raw = 142, SIGNAL_VOICE, "+16465550144", "org.thoughtcrime.securesms"),
            chat(3, raw = 142, SIGNAL_CHAT, "+16465550144", "org.thoughtcrime.securesms"),
            call(4, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp"),
            chat(5, raw = 141, WA_CHAT, "16465550144@s.whatsapp.net", "com.whatsapp"),
        )
        handle(2, SIGNAL_VOICE, "org.example.signal", label = "Signal", permission = Manifest.permission.CALL_PHONE)
        // Another app opens Signal's chat row: it is not Signal's chat.
        handle(3, SIGNAL_CHAT, "org.example.other", label = "Other", activity = CHAT_ACTIVITY)
        handle(4, WA_VOICE, "org.example.whatsapp", label = "WhatsApp", permission = Manifest.permission.CALL_PHONE)
        // A chat that also needs the call permission is no way around it.
        handle(
            5, WA_CHAT, "org.example.whatsapp", label = "WhatsApp",
            permission = Manifest.permission.CALL_PHONE, activity = CHAT_ACTIVITY,
        )

        val options = repository.callOptions(mobile, "US")

        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Signal", false, "org.example.signal", 2, SIGNAL_VOICE).guarded(chat = null),
                app("WhatsApp", false, "org.example.whatsapp", 4, WA_VOICE).guarded(chat = null),
            ),
            options,
        )
    }

    @Test
    fun aGrantedCallPermissionStillMarksTheCall() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            call(2, raw = 142, SIGNAL_VOICE, "+16465550144", "org.thoughtcrime.securesms"),
            chat(3, raw = 142, SIGNAL_CHAT, "+16465550144", "org.thoughtcrime.securesms"),
        )
        handle(2, SIGNAL_VOICE, "org.example.signal", label = "Signal", permission = Manifest.permission.CALL_PHONE)
        handle(3, SIGNAL_CHAT, "org.example.signal", label = "Signal", activity = CHAT_ACTIVITY)

        val signalChat = AppChat("org.example.signal", "org.example.signal.ChatActivity", 3, ITEM + SIGNAL_CHAT)
        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Signal", false, "org.example.signal", 2, SIGNAL_VOICE).guarded(signalChat),
            ),
            repository.callOptions(mobile, "US"),
        )
    }

    @Test
    fun chatRowsAreNeverOfferedAsCalls() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            chat(2, raw = 141, WA_CHAT, "16465550144@s.whatsapp.net", "com.whatsapp"),
        )
        handle(2, WA_CHAT, "org.example.whatsapp", label = "WhatsApp")

        assertEquals(listOf(CallOption.Phone("6465550144")), repository.callOptions(mobile, "US"))
    }

    @Test
    fun withoutContactsAccessOnlyThePhoneAppIsOffered() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        provider.rows += call(4, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp")
        handle(4, WA_VOICE, "org.example.whatsapp", label = "WhatsApp")

        assertEquals(listOf(CallOption.Phone("6465550144")), repository.callOptions(mobile, "US"))
        assertEquals(emptyList<String>(), provider.contactIds)
    }

    @Test
    fun providerFailureStillOffersThePhoneApp() = runTest {
        provider.failWith = IllegalStateException("provider gone")
        assertEquals(listOf(CallOption.Phone("6465550144")), repository.callOptions(mobile, "US"))
    }

    @Test
    fun withoutAPhoneAppOnlyOtherAppsAreOffered() = runTest {
        shadowOf(app.packageManager).removeActivity(DIALER)
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            call(4, raw = 146, "vnd.ch.threema.app.call", "ABCD1234", "ch.threema.app"),
        )
        handle(4, "vnd.ch.threema.app.call", "org.example.threema", label = "Threema")

        assertEquals(
            listOf(app("Threema", false, "org.example.threema", 4, "vnd.ch.threema.app.call")),
            repository.callOptions(mobile, "US"),
        )
    }

    @Test
    fun appsThatOpenAnyTypeDoNotHideTheOwnersCall() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            call(2, raw = 141, WA_VOICE, "16465550144@s.whatsapp.net", "com.whatsapp"),
            call(3, raw = 145, MEET_MEET_AUDIO, "+1 646-555-0144", "com.google.android.apps.tachyon"),
        )
        // A text editor that declares it opens anything matches every row too.
        handle(2, WA_VOICE, "org.example.whatsapp", label = "WhatsApp", filterType = ITEM + WA_VOICE)
        handle(2, WA_VOICE, "org.example.editor", label = "Editor", filterType = "*/*")
        handle(3, MEET_MEET_AUDIO, "org.example.editor", label = "Editor", filterType = "*/*")
        handle(3, MEET_MEET_AUDIO, "org.example.meet", label = "Meet", filterType = ITEM + MEET_MEET_AUDIO)

        assertEquals(
            listOf(
                CallOption.Phone("6465550144"),
                app("Meet", false, "org.example.meet", 3, MEET_MEET_AUDIO),
                app("WhatsApp", false, "org.example.whatsapp", 2, WA_VOICE),
            ),
            repository.callOptions(mobile, "US"),
        )
    }

    @Test
    fun theAccountsOwnAppWinsOverAnotherAppNamingTheSameType() = runTest {
        provider.rows += listOf(
            phone(1, raw = 140, "6465550144", "com.google"),
            // Signal and a fork share the mimetype; the row's account is the fork's package.
            call(2, raw = 142, SIGNAL_VOICE, "+16465550144", "org.example.fork"),
        )
        handle(2, SIGNAL_VOICE, "org.example.signal", label = "Signal", filterType = ITEM + SIGNAL_VOICE)
        handle(2, SIGNAL_VOICE, "org.example.fork", label = "Fork", filterType = ITEM + SIGNAL_VOICE)

        assertEquals(
            listOf(CallOption.Phone("6465550144"), app("Fork", false, "org.example.fork", 2, SIGNAL_VOICE)),
            repository.callOptions(mobile, "US"),
        )
    }

    @Test
    fun withoutAPhoneAppOrContactsAccessNothingIsOffered() = runTest {
        shadowOf(app.packageManager).removeActivity(DIALER)
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)

        assertEquals(emptyList<CallOption>(), repository.callOptions(mobile, "US"))
    }

    /** A phone app that opens `tel:` numbers, as on any phone. */
    private fun installDialer() {
        val packageManager = shadowOf(app.packageManager)
        packageManager.addActivityIfNotPresent(DIALER)
        packageManager.addIntentFilterForActivity(
            DIALER,
            IntentFilter(Intent.ACTION_DIAL).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("tel")
            },
        )
    }

    private fun handle(
        dataId: Long,
        mimeType: String,
        packageName: String,
        label: String?,
        permission: String? = null,
        exported: Boolean = true,
        activity: String = "CallActivity",
        filterType: String? = null,
    ) {
        // A chat is looked up in the app that handles the call, like an intent with a package.
        val chatPackage = packageName.takeIf { activity == CHAT_ACTIVITY }
        val info = ResolveInfo().apply {
            filter = filterType?.let { type -> IntentFilter(Intent.ACTION_VIEW).apply { addDataType(type) } }
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.$activity"
                this.exported = exported
                this.permission = permission
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    nonLocalizedLabel = label
                }
            }
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.withAppendedPath(Data.CONTENT_URI, dataId.toString()), ITEM + mimeType)
            .setPackage(chatPackage)
        shadowOf(app.packageManager).addResolveInfoForIntent(intent, info)
    }

    private fun app(label: String, isVideo: Boolean, packageName: String, dataId: Long, mimeType: String) =
        CallOption.App(label, isVideo, packageName, "$packageName.CallActivity", dataId, ITEM + mimeType)

    private fun CallOption.App.guarded(chat: AppChat?) = copy(needsCallPermission = true, chat = chat)

    private fun phone(id: Long, raw: Long, number: String, account: String) =
        FakeRow(id, raw, "phone_v2", account, number, null)

    private fun call(id: Long, raw: Long, mimeType: String, data1: String, account: String, data3: String? = null) =
        FakeRow(id, raw, mimeType, account, data1, data3)

    private fun chat(id: Long, raw: Long, mimeType: String, data1: String, account: String, data3: String? = null) =
        FakeRow(id, raw, mimeType, account, data1, data3)

    private fun entry(original: String, formatted: String) = PhoneEntry(
        dataId = 1,
        rawContactId = 140,
        contactId = 14,
        displayName = "Dana Testperson",
        originalNumber = original,
        formattedNumber = formatted,
        typeLabel = "Mobile",
        accountType = "com.google",
        accountLabel = "Google",
        isValid = true,
        isModified = original != formatted,
    )

    class FakeRow(
        val id: Long,
        val rawContactId: Long,
        mimeType: String,
        val accountType: String,
        val data1: String?,
        val data3: String?,
    ) {
        val mimeType = ITEM + mimeType
    }

    /** Answers data queries with [rows], for the contact and mimetypes asked for. */
    class FakeContactsProvider : ContentProvider() {
        val rows = mutableListOf<FakeRow>()
        val contactIds = mutableListOf<String>()
        var failWith: RuntimeException? = null

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            failWith?.let { throw it }
            val args = selectionArgs.orEmpty().toList()
            contactIds += args.first()
            val mimeTypes = args.drop(1).dropLast(1).toSet()
            val threema = Regex(args.last().replace(".", "\\.").replace("%", ".*"))
            val columns = projection ?: arrayOf(Data._ID)
            val cursor = MatrixCursor(columns)
            rows.filter { it.mimeType in mimeTypes || threema.matches(it.mimeType) }.forEach { row ->
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            Data._ID -> row.id
                            Data.RAW_CONTACT_ID -> row.rawContactId
                            Data.MIMETYPE -> row.mimeType
                            RawContacts.ACCOUNT_TYPE -> row.accountType
                            Data.DATA1 -> row.data1
                            Data.DATA3 -> row.data3
                            else -> null
                        }
                    },
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        val DIALER = ComponentName("org.example.dialer", "org.example.dialer.DialActivity")
        const val ITEM = "vnd.android.cursor.item/"
        const val WA_VOICE = "vnd.com.whatsapp.voip.call"
        const val WA_VIDEO = "vnd.com.whatsapp.video.call"
        const val VIBER_VOICE = "vnd.com.viber.voip.viber_number_call"
        const val SIGNAL_VOICE = "vnd.org.thoughtcrime.securesms.call"
        const val TELEGRAM_VOICE = "vnd.org.telegram.messenger.android.call"
        const val MEET_AUDIO = "com.google.android.apps.tachyon.phone.audio"
        const val MEET_VIDEO = "com.google.android.apps.tachyon.phone"
        const val MEET_MEET_AUDIO = "com.google.android.apps.tachyon.phone.meet.audio"
        const val CHAT_ACTIVITY = "ChatActivity"
        const val WA_CHAT = "vnd.com.whatsapp.profile"
        const val SIGNAL_CHAT = "vnd.org.thoughtcrime.securesms.contact"
        const val TELEGRAM_CHAT = "vnd.org.telegram.messenger.android.profile"
    }
}
