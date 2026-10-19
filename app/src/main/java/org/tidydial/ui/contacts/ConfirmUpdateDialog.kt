package org.tidydial.ui.contacts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.tidydial.R
import org.tidydial.ui.theme.TidyDialTheme

/** Asks before rewriting [count] numbers; explains that only numbers change and undo exists. */
@Composable
fun ConfirmUpdateDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_contacts), contentDescription = null) },
        title = { Text(pluralStringResource(R.plurals.list_confirm_title, count, count)) },
        text = { Text(stringResource(R.string.list_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.list_confirm_update)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview
@Composable
private fun ConfirmUpdateDialogPreview() {
    TidyDialTheme(dynamicColor = false) {
        ConfirmUpdateDialog(count = 48, onConfirm = {}, onDismiss = {})
    }
}
