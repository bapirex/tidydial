package net.evopoint.tidydial.ui.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.BackupError

/**
 * Why a backup setting did not take effect, inside the sheet or dialog that asked for it. A live
 * region, so screen readers announce it without moving focus.
 */
@Composable
fun BackupErrorText(error: BackupError, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (error) {
                BackupError.FOLDER_FAILED -> R.string.backup_folder_failed
                BackupError.SAVE_FAILED -> R.string.backup_save_failed
                BackupError.NO_PICKER -> R.string.backup_no_folder_picker
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
