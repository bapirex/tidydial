package org.tidydial.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.tidydial.R
import org.tidydial.ui.ContactFilter
import org.tidydial.ui.EntryCounts
import org.tidydial.ui.theme.TidyDialTheme

private val HorizontalSafeInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

/**
 * Lets the search field and filter chips scroll away when vertical space is tight (landscape
 * phones, very large fonts): they hide as the list scrolls up and come back as soon as it scrolls
 * down, like an enter-always app bar. Install [nestedScrollConnection] inside the large app bar's
 * connection so the title collapses first.
 */
@Stable
class HeaderCollapseState {
    /** Off: the header stays pinned and any collapse is undone. */
    var enabled: Boolean by mutableStateOf(false)

    /** How far the header is hidden, from `-height` (gone) to 0 (fully shown). */
    var offset: Float by mutableFloatStateOf(0f)
        private set

    internal var limit: Float by mutableFloatStateOf(0f)

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || available.y == 0f) return Offset.Zero
            val previous = offset
            offset = (offset + available.y).coerceIn(limit, 0f)
            return Offset(0f, offset - previous)
        }
    }

    internal fun reset() {
        offset = 0f
    }
}

/** Decides when the header collapses and keeps [HeaderCollapseState.enabled] in sync. */
@Composable
fun rememberHeaderCollapseState(enabled: Boolean): HeaderCollapseState {
    val state = remember { HeaderCollapseState() }
    SideEffect {
        if (state.enabled != enabled) {
            state.enabled = enabled
            if (!enabled) state.reset()
        }
    }
    return state
}

/** Shrinks the content by [HeaderCollapseState.offset], sliding it up under the app bar. */
private fun Modifier.collapsible(state: HeaderCollapseState?): Modifier =
    if (state == null) {
        this
    } else {
        clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            state.limit = -placeable.height.toFloat()
            val offset = state.offset.coerceAtLeast(state.limit).roundToInt()
            layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
        }
    }

/**
 * Collapsing large flexible app bar with a counts subtitle, followed by the search field and the
 * filter chips. The whole header shares one background that tints as the title collapses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsTopBar(
    counts: EntryCounts,
    isLoading: Boolean,
    loadError: Boolean,
    filter: ContactFilter,
    canUndo: Boolean,
    searchState: TextFieldState,
    scrollBehavior: TopAppBarScrollBehavior,
    onFilterChange: (ContactFilter) -> Unit,
    onShowOptions: () -> Unit,
    onRefresh: () -> Unit,
    onUndo: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
    headerCollapse: HeaderCollapseState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.drawBehind {
            val fraction = scrollBehavior.state.collapsedFraction
            drawRect(lerp(scheme.surface, scheme.surfaceContainer, fraction))
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LargeFlexibleTopAppBar(
            // Line the title and actions up with the capped content column on wide screens.
            modifier = Modifier.widthIn(max = MaxContentWidth + 32.dp),
            title = { Text(stringResource(R.string.app_name)) },
            subtitle = if (loadError && counts.total == 0) {
                null
            } else {
                { Text(summaryText(counts, isLoading), maxLines = 1) }
            },
            actions = {
                IconButton(onClick = onShowOptions) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = stringResource(R.string.format_options),
                    )
                }
                OverflowMenu(
                    canUndo = canUndo,
                    onRefresh = onRefresh,
                    onUndo = onUndo,
                    onShowAbout = onShowAbout,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            scrollBehavior = scrollBehavior,
        )
        // Search and filters only make sense once there is something to search.
        AnimatedVisibility(visible = counts.total > 0, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .collapsible(headerCollapse)
                    .fillMaxWidth()
                    .windowInsetsPadding(HorizontalSafeInsets),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.widthIn(max = MaxContentWidth + 32.dp)) {
                    SearchField(
                        state = searchState,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    FilterRow(
                        filter = filter,
                        counts = counts,
                        onFilterChange = onFilterChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun summaryText(counts: EntryCounts, isLoading: Boolean): String {
    if (isLoading) return stringResource(R.string.list_summary_loading)
    val numbers = pluralStringResource(R.plurals.number_count, counts.total, counts.total)
    if (counts.total == 0) return numbers
    val status = if (counts.changes > 0) {
        pluralStringResource(R.plurals.list_summary_need_formatting, counts.changes, counts.changes)
    } else {
        stringResource(R.string.list_summary_all_tidy)
    }
    return stringResource(R.string.join_dot, numbers, status)
}

@Composable
private fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val active = searchBarState.currentValue == SearchBarValue.Expanded
    // Back leaves the search field (and hides the keyboard) before leaving the app.
    BackHandler(enabled = active) { scope.launch { searchBarState.animateToCollapsed() } }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SearchBarDefaults.InputField(
            textFieldState = state,
            searchBarState = searchBarState,
            onSearch = {
                keyboard?.hide()
                scope.launch { searchBarState.animateToCollapsed() }
            },
            placeholder = { Text(stringResource(R.string.list_search_placeholder), maxLines = 1) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (state.text.isNotEmpty()) {
                {
                    IconButton(onClick = { state.clearText() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_clear_search),
                        )
                    }
                }
            } else {
                null
            },
            colors = SearchBarDefaults.inputFieldColors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OverflowMenu(
    canUndo: Boolean,
    onRefresh: () -> Unit,
    onUndo: () -> Unit,
    onShowAbout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.list_action_more),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            val items = buildList {
                add(MenuEntry(R.string.list_menu_refresh, R.drawable.ic_refresh, onRefresh))
                if (canUndo) add(MenuEntry(R.string.list_menu_undo, R.drawable.ic_undo, onUndo))
                add(MenuEntry(R.string.list_menu_about, R.drawable.ic_info, onShowAbout))
            }
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        text = { Text(stringResource(item.labelRes)) },
                        shape = MenuDefaults.itemShape(index, items.size).shape,
                        leadingIcon = {
                            Icon(painterResource(item.iconRes), contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

private data class MenuEntry(val labelRes: Int, val iconRes: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ContactsTopBarPreview() {
    TidyDialTheme(dynamicColor = false) {
        ContactsTopBar(
            counts = EntryCounts(total = 312, changes = 48, selected = 40, invalid = 3),
            isLoading = false,
            loadError = false,
            filter = ContactFilter.ALL,
            canUndo = true,
            searchState = rememberTextFieldState(),
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
            onFilterChange = {},
            onShowOptions = {},
            onRefresh = {},
            onUndo = {},
            onShowAbout = {},
        )
    }
}
