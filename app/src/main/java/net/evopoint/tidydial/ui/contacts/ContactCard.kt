package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.model.ContactGroup
import net.evopoint.tidydial.model.NumberItem
import net.evopoint.tidydial.model.toNumberItems
import net.evopoint.tidydial.ui.isSelectable
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * One contact: avatar, name and a status line, followed by a [NumberRow] per shown number. The
 * same number stored in several accounts is one row. A contact whose numbers are all up to date
 * gets a denser card.
 *
 * [onToggleEntries] toggles one row's data ids; [onToggleContact] gets the contact id and the
 * selectable ids of the numbers shown, so numbers hidden by the filter or search are left alone.
 * [onCall] gets the number whose call button was tapped.
 */
@Composable
fun ContactCard(
    group: ContactGroup,
    selection: Set<Long>,
    onToggleEntries: (List<Long>) -> Unit,
    onToggleContact: (Long, List<Long>) -> Unit,
    onCall: (NumberItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(group) { group.entries.toNumberItems() }
    val selectable = items.filter { it.entry.isSelectable }
    val selectableIds = selectable.flatMap { it.dataIds }
    val selectedCount = selectable.count { it.isSelectedIn(selection) }
    val anySelected = selectableIds.any { it in selection }
    val avatarSelection = when {
        selectable.isEmpty() || !anySelected -> AvatarSelection.None
        selectedCount == selectable.size -> AvatarSelection.All
        else -> AvatarSelection.Partial
    }
    val allTidy = items.all { it.entry.isValid && !it.entry.isModified }
    val showAccounts = remember(group) { group.entries.map { it.accountLabel }.distinct().size > 1 }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(if (allTidy) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (allTidy) 0.dp else 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (allTidy) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContactAvatar(
                    contactId = group.contactId,
                    name = group.displayName,
                    initials = group.initials,
                    selection = avatarSelection,
                    onToggle = if (selectable.isEmpty()) {
                        null
                    } else {
                        { onToggleContact(group.contactId, selectableIds) }
                    },
                    size = if (allTidy) 40.dp else 48.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = contactStatus(items, selectable.size, selectedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items.forEach { item ->
                NumberRow(
                    item = item,
                    selected = item.isSelectedIn(selection),
                    onToggle = { onToggleEntries(item.dataIds) },
                    onCall = { onCall(item) },
                    // Tidy cards have a narrower inset; this keeps the call buttons in one column.
                    modifier = if (allTidy) Modifier.padding(end = 4.dp) else Modifier,
                    showAccount = showAccounts,
                )
            }
        }
    }
}

@Composable
private fun contactStatus(items: List<NumberItem>, selectable: Int, selected: Int): String {
    val invalid = items.count { !it.entry.isValid }
    return when {
        selectable > 0 -> pluralStringResource(R.plurals.list_contact_selected_of, selectable, selected, selectable)
        invalid > 0 -> pluralStringResource(R.plurals.list_contact_invalid, invalid, invalid)
        else -> stringResource(R.string.list_contact_up_to_date)
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ContactCardPreview() {
    TidyDialTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SampleData.groups.take(3).forEach { group ->
                ContactCard(
                    group = group,
                    selection = SampleData.defaultSelection,
                    onToggleEntries = {},
                    onToggleContact = { _, _ -> },
                    onCall = {},
                )
            }
        }
    }
}
