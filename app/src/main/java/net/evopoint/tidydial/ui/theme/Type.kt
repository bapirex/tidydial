package net.evopoint.tidydial.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/** The baseline M3 type scale (including the Expressive emphasized styles) on the system font. */
val TidyDialTypography: Typography = Typography()

private const val TABULAR_FIGURES = "tnum"

/**
 * Returns this style with tabular (fixed-width) figures, so digits line up between the original
 * and formatted number. Phone numbers are always laid out left-to-right, even in RTL locales.
 */
fun TextStyle.phoneNumber(): TextStyle =
    copy(fontFeatureSettings = TABULAR_FIGURES, textDirection = TextDirection.Ltr)

/** Styles for phone numbers, built on the current [MaterialTheme.typography]. */
object PhoneNumberTextStyles {
    /** Primary number in a list row (the formatted result). */
    val large: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.phoneNumber()

    /** Secondary number in a list row (the original, usually struck through). */
    val medium: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium.phoneNumber()

    /** Small inline numbers such as calling codes and live examples. */
    val small: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.phoneNumber()
}
