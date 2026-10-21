package net.evopoint.tidydial.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Robolectric only provides a working org.json; the journal itself has no Android dependencies.
@RunWith(RobolectricTestRunner::class)
class UndoJournalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val entries = listOf(
        JournalEntry(dataId = 1L, before = "2125550199", after = "+1 212-555-0199"),
        JournalEntry(dataId = 42L, before = "0049 30 \"123\"", after = "+49 30 123"),
        JournalEntry(dataId = Long.MAX_VALUE, before = "2125550199,12", after = "+1 212-555-0199;12"),
        JournalEntry(dataId = 7L, before = "٠١٢٣ ✓", after = "\\path/ünïcödé"),
    )

    @Test
    fun roundTrip() = runBlocking<Unit> {
        val dir = tempFolder.newFolder()
        UndoJournal(dir).save(entries)

        assertEquals(entries, UndoJournal(dir).load())
        assertTrue(File(dir, UndoJournal.FILE_NAME).exists())
        assertFalse(File(dir, "${UndoJournal.FILE_NAME}.tmp").exists())
    }

    @Test
    fun saveReplacesPreviousJournal() = runBlocking<Unit> {
        val journal = UndoJournal(tempFolder.newFolder())
        journal.save(entries)
        journal.save(entries.take(1))
        assertEquals(entries.take(1), journal.load())
    }

    @Test
    fun missingJournalLoadsEmpty() = runBlocking<Unit> {
        assertEquals(emptyList<JournalEntry>(), UndoJournal(tempFolder.newFolder()).load())
    }

    @Test
    fun clearAndSaveEmptyRemoveTheFile() = runBlocking<Unit> {
        val dir = tempFolder.newFolder()
        val journal = UndoJournal(dir)
        val file = File(dir, UndoJournal.FILE_NAME)

        journal.save(entries)
        journal.clear()
        assertFalse(file.exists())
        assertTrue(journal.load().isEmpty())

        journal.save(entries)
        journal.save(emptyList())
        assertFalse(file.exists())
    }

    @Test
    fun corruptJournalLoadsEmpty() = runBlocking<Unit> {
        val dir = tempFolder.newFolder()
        File(dir, UndoJournal.FILE_NAME).writeText("{not json")
        assertTrue(UndoJournal(dir).load().isEmpty())
    }
}
