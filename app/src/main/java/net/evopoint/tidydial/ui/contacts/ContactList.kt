package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.ContactFilter
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/** Content is capped at this width and centered on tablets and foldables. */
val MaxContentWidth: Dp = 840.dp

/**
 * The grouped list of contact cards. [contentPadding] comes from the Scaffold (insets) and
 * [bottomClearance] keeps the last card above the floating toolbar.
 */
@Composable
fun ContactList(
    state: UiState,
    onAction: (UiAction) -> Unit,
    contentPadding: PaddingValues,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val layoutDirection = LocalLayoutDirection.current
    val showsAllTidy = state.showsAllTidy
    val locale = LocalConfiguration.current.locales[0]
    val indexer = remember(locale) { ContactIndexer(locale) }
    val sections = remember(state.groups, showsAllTidy, indexer) {
        if (state.groups.size < MinContactsForFastScroll) {
            emptyList()
        } else {
            indexer.sections(state.groups.map { it.displayName }, firstItemIndex = if (showsAllTidy) 1 else 0)
        }
    }
    val showRail = sections.size >= MinSectionsForFastScroll
    val bottom = contentPadding.calculateBottomPadding() + bottomClearance
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp +
                    if (showRail) FastScrollRailWidth else 0.dp,
                top = 8.dp,
                bottom = bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showsAllTidy) {
                item(key = "all-tidy", contentType = "all-tidy") {
                    AllTidyCard(
                        total = state.counts.total,
                        modifier = Modifier
                            .widthIn(max = MaxContentWidth)
                            .animateItem(),
                    )
                }
            }
            items(state.groups, key = { it.contactId }, contentType = { "contact" }) { group ->
                ContactCard(
                    group = group,
                    selection = state.selection,
                    onToggleEntries = { onAction(UiAction.ToggleEntries(it)) },
                    onToggleContact = { id, ids -> onAction(UiAction.ToggleContact(id, ids)) },
                    onCall = { onAction(UiAction.Call(it.dataIds)) },
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .animateItem(),
                )
            }
        }
        if (showRail) {
            FastScrollRail(
                sections = sections,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                        top = 8.dp,
                        bottom = bottom,
                    ),
            )
        }
    }
}

/** The rail only earns its space on lists long enough to need it. */
private const val MinContactsForFastScroll = 20
private const val MinSectionsForFastScroll = 3

/** The unfiltered list of an address book with nothing to fix. */
private val UiState.showsAllTidy: Boolean
    get() = filter == ContactFilter.ALL && query.isBlank() && groups.isNotEmpty() &&
        counts.changes == 0 && counts.invalid == 0

/** "All N numbers are tidy" banner on top of a list with nothing to update. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AllTidyCard(total: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CheckIcon, contentDescription = null, tint = scheme.onPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pluralStringResource(R.plurals.list_all_tidy_title, total, total),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.list_all_tidy_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Initial load: a centered Expressive loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactsLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.list_loading)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(
            modifier = Modifier
                .size(72.dp)
                .semantics { contentDescription = description },
        )
    }
}

/** Empty, no-match and error states, chosen from [state]. */
@Composable
fun ContactsMessage(
    state: UiState,
    onAction: (UiAction) -> Unit,
    onClearSearch: () -> Unit,
    onShowOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showAll = MessageAction(stringResource(R.string.list_show_all), {
        onAction(UiAction.SetFilter(ContactFilter.ALL))
    })
    when {
        state.loadError -> ListMessage(
            iconRes = R.drawable.ic_error,
            title = stringResource(R.string.error_load_contacts),
            body = stringResource(R.string.list_error_body),
            action = MessageAction(
                label = stringResource(R.string.list_error_retry),
                onClick = { onAction(UiAction.Refresh) },
                primary = true,
            ),
            isError = true,
            modifier = modifier,
        )

        // The user's own "Skip numbers from" switches hide everything; Refresh would not help.
        state.allAccountsSkipped -> ListMessage(
            iconRes = R.drawable.ic_tune,
            title = stringResource(R.string.list_all_skipped_title),
            body = stringResource(R.string.list_all_skipped_body),
            action = MessageAction(stringResource(R.string.format_options), onShowOptions, primary = true),
            modifier = modifier,
        )

        state.hasNoContacts -> ListMessage(
            iconRes = R.drawable.ic_contacts,
            title = stringResource(R.string.list_empty_title),
            body = stringResource(R.string.list_empty_body),
            action = MessageAction(stringResource(R.string.list_menu_refresh), {
                onAction(UiAction.Refresh)
            }),
            modifier = modifier,
        )

        state.query.isNotBlank() -> ListMessage(
            iconRes = R.drawable.ic_search,
            title = stringResource(R.string.list_no_matches_title),
            body = stringResource(R.string.list_no_matches_body, state.query.trim()),
            action = MessageAction(stringResource(R.string.action_clear_search), onClearSearch),
            modifier = modifier,
        )

        state.filter == ContactFilter.SELECTED -> ListMessage(
            iconRes = R.drawable.ic_select_all,
            title = stringResource(R.string.list_no_selection_title),
            body = stringResource(R.string.list_no_selection_body),
            action = showAll,
            modifier = modifier,
        )

        state.filter == ContactFilter.INVALID -> ListMessage(
            iconRes = R.drawable.ic_check_circle,
            title = stringResource(R.string.list_no_invalid_title),
            body = stringResource(R.string.list_no_invalid_body),
            action = showAll,
            modifier = modifier,
        )

        else -> ListMessage(
            iconRes = R.drawable.ic_check_circle,
            title = stringResource(R.string.list_no_changes_title),
            body = stringResource(R.string.list_no_changes_body),
            action = if (state.filter == ContactFilter.ALL) null else showAll,
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun ContactListPreview() {
    TidyDialTheme(dynamicColor = false) {
        ContactList(
            state = SampleData.uiState(),
            onAction = {},
            contentPadding = PaddingValues(),
            bottomClearance = 96.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 600)
@Composable
private fun ContactsMessagePreview() {
    TidyDialTheme(dynamicColor = false) {
        ContactsMessage(
            state = SampleData.uiState(entries = emptyList(), selection = emptySet()),
            onAction = {},
            onClearSearch = {},
            onShowOptions = {},
        )
    }
}
