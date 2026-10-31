package net.evopoint.tidydial.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fictional numbers only: US 555-01xx and NZ +64 21 123 4567. */
class CallRowsTest {

    private val formatter = PhoneNumberFormatter()
    private var nextId = 1L

    private fun row(rawContactId: Long, mimeType: String, data1: String?, data3: String? = null, account: String = "") =
        ContactDataRow(nextId++, rawContactId, ITEM + mimeType, account, data1, data3)

    private fun phone(rawContactId: Long, number: String, account: String = "com.google") =
        row(rawContactId, "phone_v2", number, account = account)

    private fun match(rows: List<ContactDataRow>, vararg numbers: String, region: String = "US") =
        callRowsFor(rows, numbers.toList(), region, formatter).map { it.row.mimeType.removePrefix(ITEM) to it.isVideo }

    @Test
    fun whatsAppJidMatchesTheShownNumber() {
        val rows = listOf(
            phone(10, "2125550101"),
            row(11, WA_VOICE, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
            row(11, WA_VIDEO, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
        )
        assertEquals(listOf(WA_VOICE to false, WA_VIDEO to true), match(rows, "2125550101", "+1 212-555-0101"))
    }

    @Test
    fun rowsOfAnotherNumberOfTheSameContactAreLeftOut() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            phone(10, "+1 212-555-0102"),
            row(11, WA_VOICE, "12125550102@s.whatsapp.net", account = "com.whatsapp"),
            row(12, SIGNAL_VOICE, "(212) 555-0102", account = "org.thoughtcrime.securesms"),
        )
        assertTrue(match(rows, "+1 212-555-0101").isEmpty())
        assertEquals(listOf(WA_VOICE to false, SIGNAL_VOICE to false), match(rows, "+1 212-555-0102"))
    }

    @Test
    fun numbersAreReadInTheConfiguredCountry() {
        val rows = listOf(
            phone(10, "021 123 4567"),
            row(11, WA_VOICE, "64211234567@s.whatsapp.net", account = "com.whatsapp"),
        )
        assertEquals(listOf(WA_VOICE to false), match(rows, "021 123 4567", region = "NZ"))
        assertTrue(match(rows, "021 123 4567", region = "US").isEmpty())
    }

    @Test
    fun duplicateRowsOfOneAppCountOnce() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            row(11, WA_VOICE, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
            row(11, WA_VOICE, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
        )
        val calls = callRowsFor(rows, listOf("+1 212-555-0101"), "US", formatter)
        assertEquals(listOf(rows[1].dataId), calls.map { it.row.dataId })
    }

    @Test
    fun chatProfileAndPaidCallRowsAreNeverCalls() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            row(11, "vnd.com.whatsapp.profile", "12125550101@s.whatsapp.net"),
            row(12, "vnd.com.viber.voip.viber_out_call_none_viber", "+12125550101"),
            row(12, "vnd.com.viber.voip.viber_number_message", "+12125550101"),
            row(12, "vnd.com.viber.voip.viber_number_call", "+12125550101"),
        )
        assertEquals(listOf("vnd.com.viber.voip.viber_number_call" to false), match(rows, "+1 212-555-0101"))
    }

    @Test
    fun telegramNumberComesFromTheSummaryText() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            row(11, TELEGRAM_VOICE, "123456789", "Voice call +1 212 555 0101"),
            row(11, TELEGRAM_VIDEO, "123456789", "Video call +1 212 555 0101"),
        )
        assertEquals(listOf(TELEGRAM_VOICE to false, TELEGRAM_VIDEO to true), match(rows, "+1 212-555-0101"))
    }

    @Test
    fun rowWithoutANumberUsesThePhoneRowsOfItsOwnRawContact() {
        // Telegram hides this person's number: the summary holds their name.
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            phone(10, "+1 212-555-0102"),
            phone(11, "+1 212-555-0102", account = "org.telegram.messenger"),
            row(11, TELEGRAM_VOICE, "123456789", "Voice call Avery Sample"),
        )
        assertTrue(match(rows, "+1 212-555-0101").isEmpty())
        assertEquals(listOf(TELEGRAM_VOICE to false), match(rows, "+1 212-555-0102"))
    }

    @Test
    fun threemaIsOnlyOfferedWhenTheContactHasOneNumber() {
        val threema = row(11, "vnd.ch.threema.app.call", "ABCD1234", "Call ABCD1234", account = "ch.threema.app")
        val oneNumber = listOf(phone(10, "2125550101"), phone(12, "+1 212-555-0101", account = "com.whatsapp"), threema)
        assertEquals(listOf("vnd.ch.threema.app.call" to false), match(oneNumber, "2125550101"))

        val twoNumbers = listOf(phone(10, "2125550101"), phone(10, "2125550102"), threema)
        assertTrue(match(twoNumbers, "2125550101").isEmpty())
    }

    @Test
    fun whatsAppJidWithoutANumberFallsBackToTheSummary() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            phone(10, "+1 212-555-0102"),
            row(11, WA_VOICE, "98765@lid", "Voice call +1 212-555-0102"),
            row(11, WA_VIDEO, "98765@lid", "Video call"),
        )
        assertEquals(listOf(WA_VOICE to false), match(rows, "+1 212-555-0102"))
    }

    @Test
    fun chatRowsAreFoundForTheSameNumberInTheSameAccount() {
        val rows = listOf(
            phone(10, "+1 212-555-0101"),
            phone(10, "+1 212-555-0102"),
            row(11, WA_VOICE, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
            row(11, WA_CHAT, "12125550102@s.whatsapp.net", account = "com.whatsapp"),
            // WhatsApp Business has its own chat row; it is not WhatsApp's.
            row(12, WA_BUSINESS_CHAT, "12125550101@s.whatsapp.net", account = "com.whatsapp.w4b"),
            row(11, WA_CHAT, "12125550101@s.whatsapp.net", account = "com.whatsapp"),
            row(13, SIGNAL_VOICE, "+12125550101", account = "org.thoughtcrime.securesms"),
            // A Signal fork (Molly) shares the mimetype, in its own account.
            row(14, SIGNAL_CHAT, "+12125550101", account = "im.molly.app"),
            // Telegram for a person who hides their number: matched by its raw contact's phone.
            phone(15, "+1 212-555-0101", account = "org.telegram.messenger"),
            row(15, TELEGRAM_VOICE, "123456789", "Voice call", account = "org.telegram.messenger"),
            row(15, TELEGRAM_CHAT, "123456789", "Message", account = "org.telegram.messenger"),
        )

        val chats = callRowsFor(rows, listOf("+1 212-555-0101"), "US", formatter)
            .associate { it.row.mimeType.removePrefix(ITEM) to it.chat?.dataId }

        assertEquals(mapOf(WA_VOICE to 6L, SIGNAL_VOICE to null, TELEGRAM_VOICE to 11L), chats)
    }

    @Test
    fun knownMimeTypesIncludeThreemaFlavors() {
        assertEquals(false, CallMimeTypes.of(ITEM + "vnd.ch.threema.app.work.call")?.isVideo)
        assertEquals(true, CallMimeTypes.of(ITEM + "com.google.android.apps.tachyon.phone")?.isVideo)
        assertNull(CallMimeTypes.of(ITEM + "vnd.ch.threema.app.profile"))
        assertNull(CallMimeTypes.of(ITEM + "vnd.com.viber.voip.viber_out_call_viber"))
    }

    private companion object {
        const val ITEM = "vnd.android.cursor.item/"
        const val WA_VOICE = "vnd.com.whatsapp.voip.call"
        const val WA_VIDEO = "vnd.com.whatsapp.video.call"
        const val SIGNAL_VOICE = "vnd.org.thoughtcrime.securesms.call"
        const val TELEGRAM_VOICE = "vnd.org.telegram.messenger.android.call"
        const val TELEGRAM_VIDEO = "vnd.org.telegram.messenger.android.call.video"
        const val WA_CHAT = "vnd.com.whatsapp.profile"
        const val WA_BUSINESS_CHAT = "vnd.com.whatsapp.w4b.profile"
        const val SIGNAL_CHAT = "vnd.org.thoughtcrime.securesms.contact"
        const val TELEGRAM_CHAT = "vnd.org.telegram.messenger.android.profile"
    }
}
