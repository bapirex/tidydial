package net.evopoint.tidydial.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.BackupError
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * The backup could not be written to [folderName] (null: no folder), so the update did not
 * start. Offers to choose the folder again, which continues the update, or to run this one
 * update without a backup (backups stay on). [error]: choosing the folder failed. [tooLarge]: the
 * backup would be too large to restore, which no folder fixes, so only the update without a
 * backup is offered.
 */
@Composable
fun BackupFailedDialog(
    folderName: String?,
    error: BackupError?,
    onChooseFolder: () -> Unit,
    onUpdateWithoutBackup: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tooLarge: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
        title = { Text(stringResource(R.string.backup_failed_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    when {
                        tooLarge -> stringResource(R.string.backup_failed_body_too_large)
                        folderName != null -> stringResource(R.string.backup_failed_body, folderName)
                        else -> stringResource(R.string.backup_failed_body_no_folder)
                    },
                )
                if (error != null) BackupErrorText(error)
            }
        },
        confirmButton = {
            if (tooLarge) {
                TextButton(onClick = onUpdateWithoutBackup) {
                    Text(stringResource(R.string.backup_failed_update_anyway))
                }
            } else {
                TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.backup_failed_choose)) }
            }
        },
        dismissButton = {
            if (tooLarge) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            } else {
                // A FlowRow, not a Row: with a large font "Cancel" moves under "Update without
                // backup" instead of being squeezed to nothing beside it.
                FlowRow {
                    TextButton(onClick = onUpdateWithoutBackup) {
                        Text(stringResource(R.string.backup_failed_update_anyway))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        },
    )
}

@Preview
@Composable
private fun BackupFailedDialogPreview() {
    TidyDialTheme(dynamicColor = false) {
        BackupFailedDialog(
            folderName = "TidyDial backups",
            error = null,
            onChooseFolder = {},
            onUpdateWithoutBackup = {},
            onDismiss = {},
        )
    }
}
