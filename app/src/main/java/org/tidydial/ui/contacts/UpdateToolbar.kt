package org.tidydial.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.tidydial.R
import org.tidydial.ui.theme.TidyDialTheme

/**
 * Expressive floating toolbar with selection actions and the "Update N" button. The update button
 * slides away when nothing is selected, and the whole toolbar exits while scrolling down when a
 * [scrollBehavior] is given.
 *
 * The toolbar's own FAB slot is a fixed-size square, too small for a counted label, so the update
 * action is a filled button inside the vibrant toolbar instead. "Select all shown" is disabled
 * when [canSelectMore] is false (every shown number is selected, or none can be).
 */
@Composable
fun UpdateToolbar(
    selectedCount: Int,
    canSelectMore: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
) {
    val scheme = MaterialTheme.colorScheme
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
    ) {
        IconButton(onClick = onSelectAll, enabled = canSelectMore) {
            Icon(
                painter = painterResource(R.drawable.ic_select_all),
                contentDescription = stringResource(R.string.list_select_all_visible),
            )
        }
        IconButton(onClick = onClear, enabled = selectedCount > 0) {
            Icon(
                painter = painterResource(R.drawable.ic_deselect),
                contentDescription = stringResource(R.string.list_clear_selection),
            )
        }
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            val description = pluralStringResource(
                R.plurals.list_update_button_description,
                selectedCount,
                selectedCount,
            )
            Row {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onUpdate,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .semantics { contentDescription = description },
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.list_update_button,
                            selectedCount,
                            selectedCount,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun UpdateToolbarPreview() {
    TidyDialTheme(dynamicColor = false) {
        UpdateToolbar(selectedCount = 48, canSelectMore = true, onSelectAll = {}, onClear = {}, onUpdate = {})
    }
}
