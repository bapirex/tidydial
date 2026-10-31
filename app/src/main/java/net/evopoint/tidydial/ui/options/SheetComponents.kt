package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Horizontal gutter between the sheet edge and grouped list cards. */
internal val SheetGutter = 16.dp

/**
 * `ModalBottomSheet` with the app's insets and a drag handle without the rectangular highlight.
 *
 * Material 3 wraps the default drag handle in an unclipped clickable box, so its focus and press
 * state layer fills a grey rectangle behind the pill (visible when a stacked sheet gets focus).
 * The sheet chrome gets a small round ripple instead, and [content] gets the normal ripple back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TidyDialSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentIndication = LocalIndication.current
    CompositionLocalProvider(LocalIndication provides DragHandleIndication) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
        ) {
            CompositionLocalProvider(LocalIndication provides contentIndication) {
                content()
            }
        }
    }
}

/** A round highlight centered on the drag handle pill. */
internal val DragHandleIndication: IndicationNodeFactory = ripple(bounded = false, radius = 24.dp)

/** A modal sheet state that opens straight to full height, skipping the half-expanded stop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberExpandedSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)

/** Segmented list item colors that lift the cards above the sheet in light and dark themes. */
@Composable
internal fun sheetListItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright)

/**
 * Title and optional subtitle at the top of a sheet. A subtitle that is a phone number takes
 * [subtitleStyle] `PhoneNumberTextStyles.medium`, so it stays left-to-right in RTL locales.
 */
@Composable
internal fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SheetGutter * 2, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A titled group of segmented list items: a `titleSmall` primary header, an optional
 * explanation and the [content] stacked with the segmented gap.
 */
@Composable
internal fun SheetSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = SheetGutter)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = SheetGutter, end = SheetGutter, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SheetGutter, end = SheetGutter, bottom = 12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            content()
        }
    }
}

/** A country flag emoji on a tonal circle. Decorative: the country name is always next to it. */
@Composable
internal fun FlagBadge(flagEmoji: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Sized in dp like an icon, so the flag stays inside its circle at large font scales.
            val fontSize = with(LocalDensity.current) { 22.dp.toSp() }
            Text(text = flagEmoji, fontSize = fontSize, textAlign = TextAlign.Center)
        }
    }
}
