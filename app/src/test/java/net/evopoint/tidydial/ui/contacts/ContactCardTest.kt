package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.ui.ContactFilter
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContactCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<UiAction>()

    private fun setList(state: UiState) {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                ContactList(
                    state = state,
                    onAction = { actions += it },
                    contentPadding = PaddingValues(),
                    bottomClearance = 16.dp,
                )
            }
        }
    }

    @Test
    fun avatarTogglesOnlyTheShownNumbers() {
        // Avery's work number (102) is unticked, so the Selected filter shows 101 and 103.
        val selection = SampleData.defaultSelection - 102L
        setList(SampleData.uiState(filter = ContactFilter.SELECTED, selection = selection))

        composeRule.onNodeWithContentDescription("Clear numbers of Avery Sample").performClick()

        assertEquals(listOf(UiAction.ToggleContact(1L, listOf(101L, 103L))), actions)
    }

    @Test
    fun sameNumberInTwoAccountsIsOneRowThatTogglesBoth() {
        val entries = SampleData.mostlyTidyEntries
        val state = SampleData.uiState(entries = entries, selection = setOf(1301L, 1302L))
        setList(state)

        // One row (and one checkbox) for the number stored in Google and Google Meet.
        composeRule.onAllNodesWithText("Casey Demo").assertCountEquals(1)
        val row = composeRule.onNodeWithContentDescription(
            "Mobile, Google and Google Meet, 4155550133 will become +1 415-555-0133",
        )
        row.assertIsOn()
        row.performClick()
        assertEquals(listOf(UiAction.ToggleEntries(listOf(1301L, 1302L))), actions)
    }

    @Test
    fun partlySelectedDuplicateShowsUnchecked() {
        setList(SampleData.uiState(entries = SampleData.mostlyTidyEntries, selection = setOf(1301L)))
        composeRule.onNodeWithContentDescription(
            "Mobile, Google and Google Meet, 4155550133 will become +1 415-555-0133",
        ).assertIsOff()
    }

    @Test
    fun cardStatusCountsMergedNumbersOnce() {
        setList(SampleData.uiState(entries = SampleData.mostlyTidyEntries, selection = setOf(1301L, 1302L)))
        // Casey's number is in two accounts but counts once; Frankie's is not selected.
        composeRule.onAllNodesWithText("1 of 1 selected").assertCountEquals(1)
        composeRule.onAllNodesWithText("0 of 1 selected").assertCountEquals(1)
    }

    @Test
    fun upToDateNumbersKeepTheirTalkBackDescription() {
        setList(SampleData.uiState(entries = SampleData.tidyEntries, selection = emptySet()))
        composeRule.onNodeWithContentDescription("Mobile, Google, +1 646-555-0144, already formatted")
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            "Mobile, Google and WhatsApp, +1 646-555-0144, already formatted",
        ).assertExists()
        composeRule.onNodeWithContentDescription("Work, Google, +1 310-555-0145, already formatted")
            .assertExists()
    }
}
