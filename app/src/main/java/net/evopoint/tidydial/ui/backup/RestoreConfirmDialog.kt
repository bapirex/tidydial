package net.evopoint.tidydial.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.data.BackupKind
import net.evopoint.tidydial.ui.RestoreConfirmation
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Asks before restoring numbers from a backup: how many, from when, what kind of backup and
 * what is left alone. The date is the backup's own wall-clock time, as in its file name.
 */
@Composable
fun RestoreConfirmDialog(
    confirmation: RestoreConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(confirmation.createdAt, locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(confirmation.createdAt.toLocalDateTime())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_settings_backup_restore), contentDescription = null) },
        title = {
            Text(pluralStringResource(R.plurals.restore_confirm_title, confirmation.count, confirmation.count, date))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        when (confirmation.kind) {
                            BackupKind.CHANGES -> R.string.restore_confirm_changes
                            BackupKind.SNAPSHOT -> R.string.restore_confirm_snapshot
                        },
                    ),
                )
                if (confirmation.changedSince > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.restore_confirm_kept,
                            confirmation.changedSince,
                            confirmation.changedSince,
                        ),
                    )
                }
                if (confirmation.notFound > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.restore_confirm_not_found,
                            confirmation.notFound,
                            confirmation.notFound,
                        ),
                    )
                }
                Text(stringResource(R.string.restore_confirm_undo))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview
@Composable
private fun RestoreConfirmDialogPreview() {
    TidyDialTheme(dynamicColor = false) {
        RestoreConfirmDialog(SampleData.restoreConfirmation, onConfirm = {}, onDismiss = {})
    }
}
