package net.evopoint.tidydial.ui.contacts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.ProgressKind
import net.evopoint.tidydial.ui.UpdateProgress
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import net.evopoint.tidydial.ui.theme.phoneNumber

/**
 * Non-dismissable sheet shown while an update, undo or restore writes to the contacts provider.
 * It cannot be swiped, tapped or backed away; it closes when [progress] goes back to null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateProgressSheet(progress: UpdateProgress) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
    ) {
        UpdateProgressContent(progress)
    }
}

/** Title, determinate wavy progress and "12 of 48". */
@Composable
fun UpdateProgressContent(progress: UpdateProgress, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "updateProgress",
    )
    val title = stringResource(
        when (progress.kind) {
            ProgressKind.APPLY -> R.string.list_progress_apply_title
            ProgressKind.UNDO -> R.string.list_progress_undo_title
            ProgressKind.RESTORE -> R.string.list_progress_restore_title
        },
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.list_progress_count, progress.done, progress.total),
                style = MaterialTheme.typography.titleMedium.phoneNumber(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        LinearWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress.isBackingUp) {
            // A slow provider (a cloud folder, a first snapshot) can hold "0 of N" for a while:
            // say why, and announce it, while keeping the instruction below.
            Text(
                text = stringResource(R.string.list_progress_backing_up),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            text = stringResource(R.string.list_progress_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun UpdateProgressContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            UpdateProgressContent(UpdateProgress(done = 12, total = 48, kind = ProgressKind.APPLY))
        }
    }
}
