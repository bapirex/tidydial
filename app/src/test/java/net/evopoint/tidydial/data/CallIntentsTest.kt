package net.evopoint.tidydial.data

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.model.CallOption
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallIntentsTest {

    private fun dialed(number: String): String {
        val intent = CallOption.Phone(number).toIntent()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data!!.scheme)
        return intent.data!!.schemeSpecificPart
    }

    @Test
    fun phoneAppGetsTheNumberAsStored() {
        assertEquals("+1 212-555-0123", dialed("+1 212-555-0123"))
        assertEquals("(212) 555-0123", dialed(" (212) 555-0123 "))
    }

    @Test
    fun dialSequencesAndServiceCodesSurvive() {
        assertEquals("3105550142,12", dialed("3105550142,12"))
        assertEquals("+1 310-555-0142;12#", dialed("+1 310-555-0142;12#"))
        assertEquals("*#06#", dialed("*#06#"))
        // Encoded in the URI, so "#" does not end it as a fragment.
        assertEquals("tel:*%2306%23", CallOption.Phone("*#06#").toIntent().dataString)
    }

    @Test
    fun rfc3966NumberIsAlreadyAUri() {
        assertEquals("+1-310-555-0142;ext=12", dialed("tel:+1-310-555-0142;ext=12"))
        assertEquals("+1-310-555-0142;ext=12", dialed("TEL:+1-310-555-0142;ext=12"))
    }

    @Test
    fun rfc3966NumberKeepsEverythingAfterAHash() {
        // As RFC 3966 formatting saves a number with a PIN or a service code.
        assertEquals("+1-310-555-0142,,1234#", dialed("tel:+1-310-555-0142,,1234#"))
        assertEquals("+1-310-555-0142;12#", dialed("tel:+1-310-555-0142;12#"))
        assertEquals("*#06#", dialed("tel:*#06#"))
        // Already escaped in the stored text.
        assertEquals("+1-310-555-0142,,1234#", dialed("tel:+1-310-555-0142,,1234%23"))
    }

    @Test
    fun appCallOpensTheCheckedActivityOnTheDataRow() {
        val option = CallOption.App(
            label = "WhatsApp",
            isVideo = true,
            packageName = "org.example.whatsapp",
            activityName = "org.example.whatsapp.CallActivity",
            dataId = 42,
            mimeType = "vnd.android.cursor.item/vnd.com.whatsapp.video.call",
        )
        val intent = option.toIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://com.android.contacts/data/42", intent.dataString)
        assertEquals(option.mimeType, intent.type)
        assertEquals("org.example.whatsapp", intent.component!!.packageName)
        assertEquals("org.example.whatsapp.CallActivity", intent.component!!.className)
    }
}
