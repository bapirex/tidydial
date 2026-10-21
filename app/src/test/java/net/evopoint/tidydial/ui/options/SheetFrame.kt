package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * Draws sheet content the way `ModalBottomSheet` does (scrim, rounded container, drag handle),
 * because the real sheet lives in a dialog window that screenshot tests cannot capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetFrame(
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    TidyDialTheme(darkTheme = darkTheme, dynamicColor = false) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .background(BottomSheetDefaults.ScrimColor),
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor,
                    tonalElevation = BottomSheetDefaults.Elevation,
                ) {
                    Column {
                        BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                        content()
                    }
                }
            }
        }
    }
}
