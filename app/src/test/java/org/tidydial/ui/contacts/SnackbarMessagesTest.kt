package org.tidydial.ui.contacts

import androidx.compose.material3.SnackbarDuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tidydial.ui.UiAction
import org.tidydial.ui.UiEvent

@RunWith(AndroidJUnit4::class)
class SnackbarMessagesTest {

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun updatedOffersUndo() {
        val spec = UiEvent.Updated(updated = 12, skippedChanged = 0, failed = 0)
            .toSnackbarSpec(resources)
        assertEquals("Updated 12 numbers", spec.message)
        assertEquals("Undo", spec.actionLabel)
        assertEquals(UiAction.UndoLast, spec.action)
        assertEquals(SnackbarDuration.Long, spec.duration)
    }

    @Test
    fun updatedMentionsSkippedAndFailed() {
        val spec = UiEvent.Updated(updated = 1, skippedChanged = 2, failed = 1)
            .toSnackbarSpec(resources)
        assertEquals(
            "Updated 1 number · 2 skipped because they changed · 1 failed",
            spec.message,
        )
    }

    @Test
    fun nothingUpdatedHasNoUndo() {
        val spec = UiEvent.Updated(updated = 0, skippedChanged = 1, failed = 0)
            .toSnackbarSpec(resources)
        assertNull(spec.action)
        assertNull(spec.actionLabel)
    }

    @Test
    fun undoneMentionsKeptNumbers() {
        val spec = UiEvent.Undone(restored = 3, skippedChanged = 1, failed = 0)
            .toSnackbarSpec(resources)
        assertEquals("Restored 3 numbers · 1 kept because it was edited since", spec.message)
        assertNull(spec.action)
    }

    @Test
    fun saveFailuresExplainWhatHappened() {
        val journal = UiEvent.JournalSaveFailed.toSnackbarSpec(resources)
        assertEquals(
            "Couldn’t save the undo record, so nothing was changed. Free up some storage and try again.",
            journal.message,
        )
        assertNull(journal.action)
        assertEquals(
            "Couldn’t save the format options",
            UiEvent.SettingsSaveFailed.toSnackbarSpec(resources).message,
        )
    }

    @Test
    fun loadFailedOffersRetry() {
        val spec = UiEvent.LoadFailed.toSnackbarSpec(resources)
        assertEquals(UiAction.Refresh, spec.action)
    }
}
