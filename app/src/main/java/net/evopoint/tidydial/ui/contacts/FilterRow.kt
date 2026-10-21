package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.ContactFilter
import net.evopoint.tidydial.ui.EntryCounts
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import net.evopoint.tidydial.ui.theme.phoneNumber

/**
 * Horizontally scrollable filter chips; each shows how many numbers it contains. Selected and
 * Invalid are hidden while empty, unless they are the current filter.
 */
@Composable
fun FilterRow(
    filter: ContactFilter,
    counts: EntryCounts,
    onFilterChange: (ContactFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactFilter.entries.forEach { option ->
            val selected = option == filter
            val optional = option == ContactFilter.SELECTED || option == ContactFilter.INVALID
            if (optional && !selected && counts.of(option) == 0) return@forEach
            FilterChip(
                selected = selected,
                onClick = { onFilterChange(option) },
                label = { ChipLabel(option.label(), counts.of(option)) },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            imageVector = CheckIcon,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ChipLabel(text: String, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge.phoneNumber(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactFilter.label(): String = stringResource(
    when (this) {
        ContactFilter.ALL -> R.string.list_filter_all
        ContactFilter.CHANGES -> R.string.list_filter_changes
        ContactFilter.SELECTED -> R.string.list_filter_selected
        ContactFilter.INVALID -> R.string.list_filter_invalid
    },
)

@Preview(showBackground = true)
@Composable
private fun FilterRowPreview() {
    TidyDialTheme(dynamicColor = false) {
        FilterRow(
            filter = ContactFilter.CHANGES,
            counts = EntryCounts(total = 312, changes = 48, selected = 40, invalid = 3),
            onFilterChange = {},
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
