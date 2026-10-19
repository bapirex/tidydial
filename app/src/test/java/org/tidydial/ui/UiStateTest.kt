package org.tidydial.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tidydial.model.AccountInfo
import org.tidydial.ui.preview.SampleData

class UiStateTest {

    @Test
    fun visibleCountsFollowTheShownGroups() {
        val all = SampleData.uiState()
        assertEquals(6, all.visibleSelectable)
        assertEquals(0, all.visibleUnselected)

        val partly = SampleData.uiState(selection = SampleData.defaultSelection - 101L - 601L)
        assertEquals(2, partly.visibleUnselected)

        // Nothing selectable under Invalid, whatever the global counts say.
        val invalid = SampleData.uiState(filter = ContactFilter.INVALID)
        assertEquals(0, invalid.visibleSelectable)
        assertEquals(6, invalid.counts.selected)

        val search = SampleData.uiState(query = "Casey")
        assertEquals(0, search.visibleSelectable)
    }

    @Test
    fun allAccountsSkippedOnlyWhenEveryAccountWithNumbersIsExcluded() {
        val excludedAll = SampleData.config.copy(
            excludedAccountTypes = SampleData.accounts.map { it.type }.toSet(),
        )
        assertTrue(
            SampleData.uiState(entries = emptyList(), selection = emptySet(), config = excludedAll)
                .allAccountsSkipped,
        )

        val excludedSome = SampleData.config.copy(excludedAccountTypes = setOf(SampleData.WHATSAPP))
        assertFalse(
            SampleData.uiState(entries = emptyList(), selection = emptySet(), config = excludedSome)
                .allAccountsSkipped,
        )
        // Numbers are loaded: never "all skipped".
        assertFalse(SampleData.uiState(config = excludedAll).allAccountsSkipped)
        // No accounts at all is simply an empty address book.
        assertFalse(
            SampleData.uiState(entries = emptyList(), selection = emptySet())
                .copy(accounts = listOf(AccountInfo("x", "X", 0)))
                .allAccountsSkipped,
        )
    }
}
