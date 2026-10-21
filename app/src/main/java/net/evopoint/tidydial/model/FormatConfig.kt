package net.evopoint.tidydial.model

enum class NumberFormatStyle {
    INTERNATIONAL,
    NATIONAL,
    E164,
    RFC3966,
}

enum class ExtensionDelimiter {
    /** Wait: the dialer asks before sending the remaining digits. */
    SEMICOLON,

    /** Pause: the dialer sends the remaining digits after a short delay. */
    COMMA,

    /** Keep the delimiter the number already uses (p/w are normalized to , and ;). */
    KEEP_ORIGINAL,
}

data class FormatConfig(
    val countryIso: String = "US",
    val formatStyle: NumberFormatStyle = NumberFormatStyle.INTERNATIONAL,
    val extensionDelimiter: ExtensionDelimiter = ExtensionDelimiter.SEMICOLON,
    val preserveShortNumbers: Boolean = true,
    val excludedAccountTypes: Set<String> = DEFAULT_EXCLUDED_ACCOUNT_TYPES,
) {
    companion object {
        /** Messaging apps mirror numbers from other accounts; editing their copies is pointless. */
        val DEFAULT_EXCLUDED_ACCOUNT_TYPES: Set<String> = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.viber.voip",
            "ch.threema.app",
            "com.skype.contacts.sync",
            "com.skype.raider",
            "com.facebook.auth.login",
            "com.facebook.messenger",
        )
    }
}
