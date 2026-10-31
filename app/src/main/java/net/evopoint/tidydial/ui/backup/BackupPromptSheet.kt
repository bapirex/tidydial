package net.evopoint.tidydial.ui.backup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import net.evopoint.tidydial.ui.options.SheetGutter
import net.evopoint.tidydial.ui.options.SheetHeader
import net.evopoint.tidydial.ui.options.TidyDialSheet
import net.evopoint.tidydial.ui.options.rememberExpandedSheetState
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * Asked on the first update, before anything is written: keep backups in a folder the user
 * picks, or continue without. Back and swipe call [onDismiss], which cancels the update.
 * [error]: the last choice did not take effect; shown above the buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPromptSheet(
    error: BackupError?,
    onChooseFolder: () -> Unit,
    onContinueWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    TidyDialSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        BackupPromptContent(error = error, onChooseFolder = onChooseFolder, onContinueWithout = onContinueWithout)
    }
}

/** The body of [BackupPromptSheet] with its sticky footer. Stateless. */
@Composable
fun BackupPromptContent(
    onChooseFolder: () -> Unit,
    onContinueWithout: () -> Unit,
    modifier: Modifier = Modifier,
    error: BackupError? = null,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
        ) {
            SheetHeader(
                title = stringResource(R.string.backup_prompt_title),
                subtitle = stringResource(R.string.backup_prompt_body),
            )
            Column(
                modifier = Modifier.padding(horizontal = SheetGutter * 2, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PromptPoint(R.drawable.ic_settings_backup_restore, stringResource(R.string.backup_prompt_snapshot))
                PromptPoint(R.drawable.ic_lock, stringResource(R.string.backup_prompt_privacy))
            }
            Text(
                text = stringResource(R.string.backup_prompt_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SheetGutter * 2),
            )
        }
        if (scrollState.canScrollForward) HorizontalDivider()
        // Stacked, so the long labels never squeeze each other at large font sizes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = SheetGutter * 1.5f, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // In the footer, so it is never scrolled out of sight.
            if (error != null) BackupErrorText(error, Modifier.padding(horizontal = SheetGutter / 2, vertical = 4.dp))
            Button(
                onClick = onChooseFolder,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(stringResource(R.string.backup_prompt_choose), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onContinueWithout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text(stringResource(R.string.backup_prompt_skip), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PromptPoint(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupPromptContentPreview() {
    TidyDialTheme(dynamicColor = false) {
        BackupPromptContent(onChooseFolder = {}, onContinueWithout = {})
    }
}
