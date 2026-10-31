package net.evopoint.tidydial.model

/** One way to call a number: the phone app, or a voice or video call through another app. */
sealed interface CallOption {

    /** Opens the phone app with [number] filled in; the user still presses call there. */
    data class Phone(val number: String) : CallOption

    /**
     * A call action another app (WhatsApp, Signal…) added to the contact as a data row.
     *
     * @property label the app's name.
     * @property packageName and [activityName]: the activity that was checked to handle the row
     *   without a permission TidyDial lacks (other than CALL_PHONE); the call is started there and
     *   nowhere else.
     * @property dataId and [mimeType]: the data row the app opens.
     * @property needsCallPermission the activity only starts for apps that hold CALL_PHONE, which
     *   TidyDial asks for when this option is picked.
     * @property chat the same app's chat or profile for this number, opened instead when the call
     *   permission is refused; only looked up for options that need it.
     */
    data class App(
        val label: String,
        val isVideo: Boolean,
        val packageName: String,
        val activityName: String,
        val dataId: Long,
        val mimeType: String,
        val needsCallPermission: Boolean = false,
        val chat: AppChat? = null,
    ) : CallOption
}

/** A chat or profile data row of a contact, and the activity that was checked to open it. */
data class AppChat(
    val packageName: String,
    val activityName: String,
    val dataId: Long,
    val mimeType: String,
)
