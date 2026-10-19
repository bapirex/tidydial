package org.tidydial.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme: Material 3 Expressive with dynamic color (Material You) on Android 12+, and the
 * brand [LightColors]/[DarkColors] schemes on older devices or when [dynamicColor] is false.
 */
@Composable
fun TidyDialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = tidyDialColorScheme(darkTheme, dynamicColor)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = TidyDialShapes,
        typography = TidyDialTypography,
        content = content,
    )
}

@Composable
private fun tidyDialColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
