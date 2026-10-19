package org.tidydial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tidydial.ui.preview.SampleData

class NumberItemTest {

    private fun entry(dataId: Long, number: String, account: String, type: String = "Mobile") = PhoneEntry(
        dataId = dataId,
        rawContactId = dataId,
        contactId = 1L,
        displayName = "Casey Demo",
        originalNumber = number,
        formattedNumber = "+1 415-555-0133",
        typeLabel = type,
        accountType = account,
        accountLabel = account,
        isValid = true,
        isModified = number != "+1 415-555-0133",
    )

    @Test
    fun sameNumberFromSeveralAccountsIsOneItem() {
        val items = listOf(
            entry(1, "4155550133", "Google"),
            entry(2, "4155550133", "Google Meet"),
            entry(3, "4155550133", "Google"),
        ).toNumberItems()

        val item = items.single()
        assertEquals(listOf(1L, 2L, 3L), item.dataIds)
        assertEquals(listOf("Google", "Google Meet"), item.accountLabels)
    }

    @Test
    fun differentNumbersTypesOrStatusStaySeparate() {
        val items = listOf(
            entry(1, "4155550133", "Google"),
            entry(2, "4155550133", "Google", type = "Work"),
            entry(3, "+1 415-555-0133", "Google"),
            entry(4, "4155550133", "WhatsApp"),
        ).toNumberItems()

        assertEquals(listOf(listOf(1L, 4L), listOf(2L), listOf(3L)), items.map { it.dataIds })
    }

    @Test
    fun selectedOnlyWhenEveryRowIsSelected() {
        val item = listOf(entry(1, "4155550133", "Google"), entry(2, "4155550133", "Meet"))
            .toNumberItems()
            .single()
        assertFalse(item.isSelectedIn(setOf(1L)))
        assertTrue(item.isSelectedIn(setOf(1L, 2L, 9L)))
    }

    @Test
    fun mostlyTidySampleHasOneMergedNumber() {
        val casey = SampleData.mostlyTidyEntries.groupByContact().single { it.contactId == 13L }
        assertEquals(listOf("Google", "Google Meet"), casey.entries.toNumberItems().single().accountLabels)
    }
}
