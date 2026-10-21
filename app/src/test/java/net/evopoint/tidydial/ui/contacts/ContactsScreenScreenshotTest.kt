package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import net.evopoint.tidydial.ui.ContactFilter
import net.evopoint.tidydial.ui.ProgressKind
import net.evopoint.tidydial.ui.UiEvent
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.UpdateProgress
import net.evopoint.tidydial.ui.isSelectable
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val DIR = "src/test/screenshots"
private const val TAG = "screen"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContactsScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: UiState = SampleData.uiState(),
        dark: Boolean = false,
        events: Flow<UiEvent> = emptyFlow(),
    ) {
        composeRule.setContent {
            TidyDialTheme(darkTheme = dark, dynamicColor = false) {
                ContactsScreen(
                    state = state,
                    events = events,
                    onAction = {},
                    exampleNumber = { _, _ -> null },
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    private fun captureRoot(name: String) {
        composeRule.onRoot().captureRoboImage(filePath = "$DIR/contacts_$name.png")
    }

    @Test
    fun light() {
        setScreen(SampleData.uiState(canUndo = true))
        captureRoot("light")
    }

    // Google Play phone screenshots: 9:16 (1233x2193 px), copied to fastlane phoneScreenshots.
    @Test
    @Config(qualifiers = "w411dp-h731dp-xxhdpi")
    fun storeLight() {
        setScreen(SampleData.uiState(canUndo = true))
        captureRoot("store_light")
    }

    @Test
    @Config(qualifiers = "w411dp-h731dp-night-xxhdpi")
    fun storeDark() {
        setScreen(dark = true)
        captureRoot("store_dark")
    }

    @Test
    @Config(qualifiers = "+night")
    fun dark() {
        setScreen(dark = true)
        captureRoot("dark")
    }

    @Test
    @Config(fontScale = 1.5f)
    fun fontScale150() {
        setScreen()
        captureRoot("font_150")
    }

    @Test
    fun collapsedAfterScroll() {
        setScreen()
        composeRule.onNodeWithTag(TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        // The floating toolbar slides all the way off screen, not just by its own height.
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val toolbarTop = composeRule.onNodeWithContentDescription("Select all shown")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("toolbar top $toolbarTop is above $rootHeight", toolbarTop >= rootHeight)
        captureRoot("scrolled")
    }

    @Test
    fun mostlyTidy() {
        setScreen(
            SampleData.uiState(
                entries = SampleData.mostlyTidyEntries,
                selection = SampleData.mostlyTidyEntries.filter { it.isSelectable }.map { it.dataId }.toSet(),
            ),
        )
        captureRoot("mostly_tidy")
    }

    @Test
    @Config(qualifiers = "+night")
    fun mostlyTidyDark() {
        setScreen(
            SampleData.uiState(
                entries = SampleData.mostlyTidyEntries,
                selection = SampleData.mostlyTidyEntries.filter { it.isSelectable }.map { it.dataId }.toSet(),
            ),
            dark = true,
        )
        captureRoot("mostly_tidy_dark")
    }

    @Test
    fun allTidy() {
        setScreen(SampleData.uiState(entries = SampleData.tidyEntries, selection = emptySet()))
        // Nothing to act on: no toolbar.
        composeRule.onNodeWithContentDescription("Select all shown").assertDoesNotExist()
        captureRoot("all_tidy")
    }

    @Test
    fun allAccountsSkipped() {
        val config = SampleData.config.copy(
            excludedAccountTypes = SampleData.accounts.map { it.type }.toSet(),
        )
        setScreen(SampleData.uiState(entries = emptyList(), selection = emptySet(), config = config))
        composeRule.onNodeWithText("All numbers are skipped").assertExists()
        composeRule.onNodeWithText("No phone numbers yet").assertDoesNotExist()
        captureRoot("all_skipped")
    }

    @Test
    fun invalidFilterDisablesSelectAll() {
        setScreen(SampleData.uiState(filter = ContactFilter.INVALID))
        composeRule.onNodeWithContentDescription("Select all shown").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Clear selection").assertIsEnabled()
    }

    @Test
    fun nothingSelectableAndNothingSelectedHidesToolbar() {
        setScreen(SampleData.uiState(filter = ContactFilter.INVALID, selection = emptySet()))
        composeRule.onNodeWithContentDescription("Select all shown").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() {
        setScreen()
        captureRoot("landscape")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeScrolledHidesSearchAndFilters() {
        setScreen()
        composeRule.onNodeWithText("Search names or numbers").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search names or numbers").assertIsNotDisplayed()
        captureRoot("landscape_scrolled")
    }

    @Test
    fun portraitKeepsSearchPinned() {
        setScreen()
        composeRule.onNodeWithTag(TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Search names or numbers").assertIsDisplayed()
    }

    @Test
    fun filterChanges() {
        setScreen(SampleData.uiState(filter = ContactFilter.CHANGES))
        captureRoot("filter_changes")
    }

    @Test
    fun empty() {
        setScreen(SampleData.uiState(entries = emptyList(), selection = emptySet()))
        captureRoot("empty")
    }

    @Test
    fun noMatches() {
        setScreen(SampleData.uiState(query = "zelda"))
        captureRoot("no_matches")
    }

    @Test
    @Config(qualifiers = "+night")
    fun loadErrorDark() {
        setScreen(
            SampleData.uiState(entries = emptyList(), selection = emptySet(), loadError = true),
            dark = true,
        )
        captureRoot("error_dark")
    }

    @Test
    fun loading() {
        composeRule.mainClock.autoAdvance = false
        setScreen(SampleData.uiState(isLoading = true))
        composeRule.mainClock.advanceTimeBy(500)
        captureRoot("loading")
    }

    @Test
    fun updating() {
        composeRule.mainClock.autoAdvance = false
        setScreen(
            SampleData.uiState(progress = UpdateProgress(12, 48, ProgressKind.APPLY)),
        )
        composeRule.mainClock.advanceTimeBy(1_500)
        captureScreenRoboImage(filePath = "$DIR/contacts_updating.png")
    }

    @Test
    fun confirmDialog() {
        setScreen()
        // A semantics click opens the dialog without a touch ripple, which renders nondeterministically.
        composeRule.onNodeWithText("Update 6").performSemanticsAction(SemanticsActions.OnClick)
        // Let the dialog and toolbar entrance animations finish so frames are identical.
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        captureScreenRoboImage(filePath = "$DIR/contacts_confirm.png")
    }

    @Test
    fun updatedSnackbar() {
        setScreen(events = flowOf(UiEvent.Updated(updated = 6, skippedChanged = 1, failed = 0)))
        composeRule.mainClock.advanceTimeBy(1_000)
        captureRoot("snackbar")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun wideScreen() {
        setScreen()
        captureRoot("wide")
    }

    @Test
    fun components() {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                Components()
            }
        }
        captureRoot("components")
    }
}

@Composable
private fun Components() {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1L..7L).forEach { id ->
                ContactAvatar(
                    contactId = id,
                    name = "",
                    initials = SampleData.groups[(id - 1).toInt()].initials,
                    selection = if (id == 7L) AvatarSelection.All else AvatarSelection.None,
                    onToggle = {},
                )
            }
        }
        UpdateToolbar(selectedCount = 48, canSelectMore = true, onSelectAll = {}, onClear = {}, onUpdate = {})
        UpdateToolbar(selectedCount = 0, canSelectMore = true, onSelectAll = {}, onClear = {}, onUpdate = {})
        UpdateToolbar(selectedCount = 6, canSelectMore = false, onSelectAll = {}, onClear = {}, onUpdate = {})
    }
}
