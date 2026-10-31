package net.evopoint.tidydial.data

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.Data
import net.evopoint.tidydial.model.AppChat
import net.evopoint.tidydial.model.CallOption

/**
 * The intent that starts this call. The phone app only gets the number filled in (no call
 * permission needed); an app call goes to the exact activity that was checked when it was offered.
 */
fun CallOption.toIntent(): Intent = when (this) {
    is CallOption.Phone -> Intent(Intent.ACTION_DIAL, telUri(number))
    is CallOption.App -> appCallIntent(dataId, mimeType).setClassName(packageName, activityName)
}

/** Opens the chat in the exact activity that was checked when the call was offered. */
fun AppChat.toIntent(): Intent = appCallIntent(dataId, mimeType).setClassName(packageName, activityName)

/**
 * Opens the app's action on a contact data row (a call, or a chat), the way the system Contacts
 * app does.
 */
internal fun appCallIntent(dataId: Long, mimeType: String): Intent =
    // setDataAndType: setData and setType each clear the other.
    Intent(Intent.ACTION_VIEW).setDataAndType(ContentUris.withAppendedId(Data.CONTENT_URI, dataId), mimeType)

/**
 * A `tel:` URI for [number] exactly as stored, with pauses and waits (`,` `;`), `*` and `#`
 * intact: the dialer reads them back from the decoded scheme-specific part. Numbers stored in the
 * RFC 3966 style already start with `tel:`; they are re-encoded too, since parsing them as a URI
 * would cut the number at the first `#` (a fragment).
 */
internal fun telUri(number: String): Uri {
    val trimmed = number.trim()
    val digits = if (trimmed.startsWith(TEL_PREFIX, ignoreCase = true)) {
        // RFC 3966 text may already escape characters, such as "#" as %23.
        Uri.decode(trimmed.substring(TEL_PREFIX.length))
    } else {
        trimmed
    }
    return Uri.fromParts(TEL_SCHEME, digits, null)
}

private const val TEL_SCHEME = "tel"
private const val TEL_PREFIX = "$TEL_SCHEME:"
