package net.evopoint.tidydial.data

import android.provider.ContactsContract.CommonDataKinds.Phone

/** Where a call or chat row keeps the number it is for. */
internal enum class NumberColumn {
    /** `data1` is a WhatsApp jid such as `12125550123@s.whatsapp.net`. */
    WHATSAPP_JID,

    /** `data1` is the number, usually exactly as the contact stores it. */
    DATA1,

    /** Only the localized summary in `data3` ("Voice call +1 212-555-0123") contains it. */
    DATA3_TEXT,

    /** The row is keyed by the app's own ID (Threema), never by a number. */
    NONE,
}

/** @property chatMimeType the same app's chat or profile row, see [CallMimeTypes.CHATS]. */
internal class CallMimeType(
    val isVideo: Boolean,
    val numberColumn: NumberColumn,
    val chatMimeType: String? = null,
)

/**
 * Data rows that apps add to a contact to start a call through them, by mimetype. Only rows listed
 * here are offered: chat, profile and paid call rows (Viber Out) look alike but must never show up
 * as a call, and nothing in the provider tells them apart.
 */
internal object CallMimeTypes {
    private const val ITEM = "vnd.android.cursor.item/"
    private const val WHATSAPP_CHAT = "${ITEM}vnd.com.whatsapp.profile"
    private const val WHATSAPP_BUSINESS_CHAT = "${ITEM}vnd.com.whatsapp.w4b.profile"
    private const val SIGNAL_CHAT = "${ITEM}vnd.org.thoughtcrime.securesms.contact"
    private const val TELEGRAM_CHAT = "${ITEM}vnd.org.telegram.messenger.android.profile"

    val KNOWN: Map<String, CallMimeType> = mapOf(
        "${ITEM}vnd.com.whatsapp.voip.call" to
            CallMimeType(false, NumberColumn.WHATSAPP_JID, WHATSAPP_CHAT),
        "${ITEM}vnd.com.whatsapp.video.call" to
            CallMimeType(true, NumberColumn.WHATSAPP_JID, WHATSAPP_CHAT),
        "${ITEM}vnd.com.whatsapp.w4b.voip.call" to
            CallMimeType(false, NumberColumn.WHATSAPP_JID, WHATSAPP_BUSINESS_CHAT),
        "${ITEM}vnd.com.whatsapp.w4b.video.call" to
            CallMimeType(true, NumberColumn.WHATSAPP_JID, WHATSAPP_BUSINESS_CHAT),
        // Signal and its forks (Molly) share these; the row's account tells the apps apart.
        "${ITEM}vnd.org.thoughtcrime.securesms.call" to CallMimeType(false, NumberColumn.DATA1, SIGNAL_CHAT),
        "${ITEM}vnd.org.thoughtcrime.securesms.videocall" to CallMimeType(true, NumberColumn.DATA1, SIGNAL_CHAT),
        // data1 is the Telegram user id, not a number.
        "${ITEM}vnd.org.telegram.messenger.android.call" to
            CallMimeType(false, NumberColumn.DATA3_TEXT, TELEGRAM_CHAT),
        "${ITEM}vnd.org.telegram.messenger.android.call.video" to
            CallMimeType(true, NumberColumn.DATA3_TEXT, TELEGRAM_CHAT),
        // Free Viber-to-Viber call; the Viber Out rows call phone lines for money.
        "${ITEM}vnd.com.viber.voip.viber_number_call" to CallMimeType(false, NumberColumn.DATA1),
        "${ITEM}com.google.android.apps.tachyon.phone.audio" to CallMimeType(false, NumberColumn.DATA1),
        "${ITEM}com.google.android.apps.tachyon.phone" to CallMimeType(true, NumberColumn.DATA1),
    )

    /**
     * Chat and profile rows of apps whose call activity needs CALL_PHONE, and where they keep the
     * number. One is opened instead of the call when that permission is refused.
     */
    val CHATS: Map<String, NumberColumn> = mapOf(
        WHATSAPP_CHAT to NumberColumn.WHATSAPP_JID,
        WHATSAPP_BUSINESS_CHAT to NumberColumn.WHATSAPP_JID,
        SIGNAL_CHAT to NumberColumn.DATA1,
        // data3 is "Message +1 212-555-0123".
        TELEGRAM_CHAT to NumberColumn.DATA3_TEXT,
    )

    /** Threema flavors (work, libre…) put their application id in the mimetype; SQL LIKE syntax. */
    const val THREEMA_LIKE = "${ITEM}vnd.ch.threema.app%.call"
    private val THREEMA = Regex("""vnd\.android\.cursor\.item/vnd\.ch\.threema\.app(\.[\w.]+)?\.call""")
    private val THREEMA_CALL = CallMimeType(false, NumberColumn.NONE)

    fun of(mimeType: String): CallMimeType? =
        KNOWN[mimeType] ?: THREEMA_CALL.takeIf { THREEMA.matches(mimeType) }
}

/** A data row of one contact: a phone number, or a known call or chat action. */
internal class ContactDataRow(
    val dataId: Long,
    val rawContactId: Long,
    val mimeType: String,
    val accountType: String,
    val data1: String?,
    val data3: String?,
)

/** @property chat the same app's chat row for the same number, if the contact has one. */
internal class CallRow(val row: ContactDataRow, val isVideo: Boolean, val chat: ContactDataRow? = null)

/**
 * The call rows among [rows] (one contact's phone and call rows, from every account) that call
 * the number shown as [numbers] (as stored, and as TidyDial would format it). One row per app and
 * call type is kept.
 *
 * A row is matched by the number it carries. A row without one (Threema, or Telegram for a
 * person who hides their number) is matched by the phone numbers of its own raw contact, and
 * failing that, only if the contact has no other number, so it is never offered for the wrong one.
 * A chat row is matched the same way, in the call row's account.
 */
internal fun callRowsFor(
    rows: List<ContactDataRow>,
    numbers: List<String>,
    regionIso: String,
    formatter: PhoneNumberFormatter,
): List<CallRow> {
    val isShown = { number: String -> numbers.any { formatter.isSameNumber(number, it, regionIso) } }
    val phones = rows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE && !it.data1.isNullOrBlank() }
    val onlyNumber by lazy { phones.isNotEmpty() && phones.all { isShown(it.data1!!) } }
    val matches = { row: ContactDataRow, column: NumberColumn ->
        val own = row.callNumber(column)
        when {
            own != null -> isShown(own)
            else -> {
                val siblings = phones.filter { it.rawContactId == row.rawContactId }
                if (siblings.isEmpty()) onlyNumber else siblings.any { isShown(it.data1!!) }
            }
        }
    }
    return rows
        .mapNotNull { row ->
            val type = CallMimeTypes.of(row.mimeType) ?: return@mapNotNull null
            if (!matches(row, type.numberColumn)) return@mapNotNull null
            val chat = type.chatMimeType?.let { chatType ->
                rows.firstOrNull {
                    it.mimeType == chatType && it.accountType == row.accountType &&
                        matches(it, CallMimeTypes.CHATS.getValue(chatType))
                }
            }
            CallRow(row, type.isVideo, chat)
        }
        .distinctBy { it.row.accountType to it.row.mimeType }
}

private fun ContactDataRow.callNumber(column: NumberColumn): String? = when (column) {
    NumberColumn.WHATSAPP_JID -> {
        val user = data1?.substringBefore('@', missingDelimiterValue = "")
        val isPhoneJid = data1?.endsWith(WHATSAPP_PHONE_JID) == true
        if (isPhoneJid && !user.isNullOrEmpty() && user.all { it.isDigit() }) "+$user" else numberIn(data3)
    }

    NumberColumn.DATA1 -> data1?.takeIf { it.isNotBlank() }
    NumberColumn.DATA3_TEXT -> numberIn(data3)
    NumberColumn.NONE -> null
}

/** Phone accounts on WhatsApp; other jids (such as `@lid`) do not contain the number. */
private const val WHATSAPP_PHONE_JID = "@s.whatsapp.net"

/** An international number inside text such as "Voice call +1 212-555-0123". */
private val NUMBER_IN_TEXT = Regex("""\+[\d\s().-]{6,}""")

private fun numberIn(text: String?): String? =
    text?.let { NUMBER_IN_TEXT.find(it)?.value?.trim() }
