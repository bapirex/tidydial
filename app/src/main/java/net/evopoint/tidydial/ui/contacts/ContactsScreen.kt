package net.evopoint.tidydial.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiEvent
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.about.AboutSheet
import net.evopoint.tidydial.ui.options.FormatOptionsSheet
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/** Room under the list for the floating toolbar and its margins. */
private val ToolbarClearance = FloatingToolbarDefaults.ContainerSize + 32.dp

/** Room under the list when the toolbar is hidden. */
private val ListEndPadding = 16.dp

/** Windows shorter than this let the search field and chips scroll away. */
private val CompactHeaderHeight = 600.dp

/**
 * Main screen: collapsing app bar with search and filters, the grouped contact list with
 * pull-to-refresh, the floating update toolbar, and the confirm / progress / Snackbar flow.
 * Hosts the format options and about sheets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen(
    state: UiState,
    events: Flow<UiEvent>,
    onAction: (UiAction) -> Unit,
    exampleNumber: (String, NumberFormatStyle) -> String?,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val searchState = rememberTextFieldState(state.query)

    SearchQueryEffect(searchState, state.query, onAction)
    EventEffect(events, snackbarHostState, onAction)

    val topBarScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
    )
    val imeVisible = WindowInsets.isImeVisible
    val headerCollapse = rememberHeaderCollapseState(enabled = isCompactHeight() && !imeVisible)
    // Shown while there is something to act on in this view; it would ride on the keyboard.
    val showToolbar = !state.isLoading && !imeVisible &&
        (state.visibleSelectable > 0 || state.counts.selected > 0)

    Scaffold(
        modifier = modifier
            .nestedScroll(topBarScroll.nestedScrollConnection)
            .nestedScroll(headerCollapse.nestedScrollConnection)
            .nestedScroll(toolbarScroll),
        topBar = {
            ContactsTopBar(
                counts = state.counts,
                isLoading = state.isLoading,
                loadError = state.loadError,
                filter = state.filter,
                canUndo = state.canUndo && !state.isUpdating,
                searchState = searchState,
                scrollBehavior = topBarScroll,
                onFilterChange = { onAction(UiAction.SetFilter(it)) },
                onShowOptions = { showOptions = true },
                onRefresh = { onAction(UiAction.Refresh) },
                onUndo = { onAction(UiAction.UndoLast) },
                onShowAbout = { showAbout = true },
                headerCollapse = headerCollapse,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (showToolbar) ToolbarClearance - 16.dp else 0.dp),
            )
        },
        // safeDrawing adds the keyboard and display cutouts to the default system bars.
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            ContactsBody(
                state = state,
                onAction = onAction,
                innerPadding = innerPadding,
                bottomClearance = if (showToolbar) ToolbarClearance else ListEndPadding,
                onClearSearch = { searchState.clearText() },
                onShowOptions = { showOptions = true },
            )
            ToolbarOverlay(
                visible = showToolbar,
                state = state,
                scrollBehavior = toolbarScroll,
                onAction = onAction,
                onUpdate = { showConfirm = true },
            )
        }
    }

    if (showConfirm && state.counts.selected > 0 && !state.isUpdating) {
        ConfirmUpdateDialog(
            count = state.counts.selected,
            onConfirm = {
                showConfirm = false
                onAction(UiAction.ApplySelected)
            },
            onDismiss = { showConfirm = false },
        )
    }
    state.progress?.let { UpdateProgressSheet(it) }
    if (showOptions) {
        FormatOptionsSheet(
            config = state.config,
            detectedCountryIso = state.detectedCountryIso,
            countries = state.countries,
            accounts = state.accounts,
            exampleNumber = exampleNumber,
            onApply = { onAction(UiAction.UpdateConfig(it)) },
            onDismiss = { showOptions = false },
        )
    }
    if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
    }
}

/** Short windows (landscape phones) and very large fonts. */
@Composable
private fun isCompactHeight(): Boolean {
    val density = LocalDensity.current
    val height = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    return (height > 0.dp && height < CompactHeaderHeight) || density.fontScale >= 1.5f
}

/**
 * The floating toolbar over the content. Its parents fill the screen so the exit-always scroll
 * behavior measures the full distance to the bottom edge and hides the toolbar completely.
 */
@Composable
private fun ToolbarOverlay(
    visible: Boolean,
    state: UiState,
    scrollBehavior: FloatingToolbarScrollBehavior,
    onAction: (UiAction) -> Unit,
    onUpdate: () -> Unit,
) {
    val slide = with(LocalDensity.current) { ToolbarClearance.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn() + slideInVertically { slide },
        exit = fadeOut() + slideOutVertically { slide },
    ) {
        Box(Modifier.fillMaxSize()) {
            UpdateToolbar(
                selectedCount = state.counts.selected,
                canSelectMore = state.visibleUnselected > 0,
                onSelectAll = { onAction(UiAction.SelectAllVisible) },
                onClear = { onAction(UiAction.ClearSelection) },
                onUpdate = onUpdate,
                scrollBehavior = scrollBehavior,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    )
                    // Padding, not an offset: the scroll behavior hides the toolbar by the
                    // distance from its top to the parent's bottom, which must include this gap.
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactsBody(
    state: UiState,
    onAction: (UiAction) -> Unit,
    innerPadding: PaddingValues,
    bottomClearance: Dp,
    onClearSearch: () -> Unit,
    onShowOptions: () -> Unit,
) {
    val bodyModifier = Modifier
        .fillMaxSize()
        .padding(top = innerPadding.calculateTopPadding())
    if (state.isLoading) {
        ContactsLoading(bodyModifier)
        return
    }
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(UiAction.Refresh) },
        modifier = bodyModifier,
        state = pullState,
        enabled = !state.isUpdating,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = state.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        if (state.groups.isEmpty()) {
            val layoutDirection = LocalLayoutDirection.current
            ContactsMessage(
                state = state,
                onAction = onAction,
                onClearSearch = onClearSearch,
                onShowOptions = onShowOptions,
                modifier = Modifier.padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            )
        } else {
            ContactList(
                state = state,
                onAction = onAction,
                contentPadding = innerPadding,
                bottomClearance = bottomClearance,
            )
        }
    }
}

/** Sends search text changes to the ViewModel; the field owns its text. */
@Composable
private fun SearchQueryEffect(
    searchState: TextFieldState,
    query: String,
    onAction: (UiAction) -> Unit,
) {
    val currentQuery by rememberUpdatedState(query)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }.collect { text ->
            if (text != currentQuery) currentOnAction(UiAction.SetQuery(text))
        }
    }
}

/** Shows one Snackbar per event; a newer event replaces the one on screen. */
@Composable
private fun EventEffect(
    events: Flow<UiEvent>,
    snackbarHostState: SnackbarHostState,
    onAction: (UiAction) -> Unit,
) {
    val resources = LocalResources.current
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(events, resources) {
        events.collectLatest { event ->
            val spec = event.toSnackbarSpec(resources)
            val result = snackbarHostState.showSnackbar(
                message = spec.message,
                actionLabel = spec.actionLabel,
                duration = spec.duration,
            )
            if (result == SnackbarResult.ActionPerformed) spec.action?.let(currentOnAction)
        }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun ContactsScreenPreview() {
    TidyDialTheme(dynamicColor = false) {
        ContactsScreen(
            state = SampleData.uiState(canUndo = true),
            events = emptyFlow(),
            onAction = {},
            exampleNumber = { _, _ -> null },
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun ContactsScreenDarkPreview() {
    TidyDialTheme(darkTheme = true, dynamicColor = false) {
        ContactsScreen(
            state = SampleData.uiState(),
            events = emptyFlow(),
            onAction = {},
            exampleNumber = { _, _ -> null },
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun ContactsScreenEmptyPreview() {
    TidyDialTheme(dynamicColor = false) {
        ContactsScreen(
            state = SampleData.uiState(entries = emptyList(), selection = emptySet()),
            events = emptyFlow(),
            onAction = {},
            exampleNumber = { _, _ -> null },
        )
    }
}
