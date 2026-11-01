package net.evopoint.tidydial.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * Shown while the backup picked for a restore is read and compared with the contacts, which
 * takes seconds for a large book or a file a cloud app has to download first. Cancel and Back
 * stop it; a tap outside does not, so it is not lost by accident.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestorePreparingDialog(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier,
        properties = DialogProperties(dismissOnClickOutside = false),
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
                Text(
                    text = stringResource(R.string.restore_preparing),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview
@Composable
private fun RestorePreparingDialogPreview() {
    TidyDialTheme(dynamicColor = false) {
        RestorePreparingDialog(onCancel = {})
    }
}
