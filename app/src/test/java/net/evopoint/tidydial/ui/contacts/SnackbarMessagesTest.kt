package net.evopoint.tidydial.ui.contacts

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.data.BackupReadError
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

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
    fun restoreFromBackupOffersUndo() {
        val spec = UiEvent.RestoredFromBackup(restored = 21, skippedChanged = 2, notFound = 1, failed = 0)
            .toSnackbarSpec(resources)
        assertEquals(
            "Restored 21 numbers · 2 kept because they were edited since · 1 not found",
            spec.message,
        )
        assertEquals("Undo", spec.actionLabel)
        assertEquals(UiAction.UndoLast, spec.action)
        assertEquals(SnackbarDuration.Long, spec.duration)
    }

    @Test
    fun restoreThatWroteNothingHasNoUndo() {
        val spec = UiEvent.RestoredFromBackup(restored = 0, skippedChanged = 0, notFound = 0, failed = 3)
            .toSnackbarSpec(resources)
        assertEquals("Restored 0 numbers · 3 failed", spec.message)
        assertNull(spec.action)
    }

    @Test
    fun restoreWithoutItsBackupSaysSo() {
        val spec = UiEvent.RestoredFromBackup(restored = 3, skippedChanged = 0, notFound = 0, failed = 0, backupFailed = true)
            .toSnackbarSpec(resources)
        assertEquals("Restored 3 numbers · no backup saved", spec.message)
        assertEquals(UiAction.UndoLast, spec.action)
    }

    @Test
    fun nothingToRestoreGivesTheReasons() {
        fun message(event: UiEvent) = event.toSnackbarSpec(resources).message
        assertEquals(
            "Nothing to restore · 3 already match the backup · 1 kept because it was edited since · 2 not found",
            message(UiEvent.NothingToRestore(alreadyRestored = 3, changedSince = 1, notFound = 2)),
        )
        assertEquals(
            "Nothing to restore · 1 already matches the backup",
            message(UiEvent.NothingToRestore(alreadyRestored = 1, changedSince = 0, notFound = 0)),
        )
        // Nothing matched at all: not "changed since", but not on this phone.
        assertEquals(
            "Nothing to restore. None of these numbers were found on this phone.",
            message(UiEvent.NothingToRestore(alreadyRestored = 0, changedSince = 0, notFound = 5)),
        )
        assertEquals(
            "This backup holds no numbers",
            message(UiEvent.NothingToRestore(alreadyRestored = 0, changedSince = 0, notFound = 0)),
        )
    }

    @Test
    fun unreadContactsBlockTheBackupWithRetry() {
        val spec = UiEvent.BackupReadFailed.toSnackbarSpec(resources)
        assertEquals("Couldn’t read your contacts to back them up, so nothing was changed", spec.message)
        assertEquals("Retry", spec.actionLabel)
        assertEquals(UiAction.ApplySelected, spec.action)
    }

    @Test
    fun backupProblemsAreExplained() {
        fun message(event: UiEvent) = event.toSnackbarSpec(resources).message
        assertEquals("Couldn’t read this file", message(UiEvent.BackupUnreadable(BackupReadError.UNREADABLE)))
        assertEquals(
            "This file isn’t a TidyDial backup",
            message(UiEvent.BackupUnreadable(BackupReadError.NOT_A_BACKUP)),
        )
        assertEquals(
            "This backup comes from a newer version of TidyDial. Update the app to restore it.",
            message(UiEvent.BackupUnreadable(BackupReadError.NEWER_FORMAT)),
        )
        assertEquals("No app found to pick files", message(UiEvent.NoFilePicker))
    }

    @Test
    fun loadFailedOffersRetry() {
        val spec = UiEvent.LoadFailed.toSnackbarSpec(resources)
        assertEquals(UiAction.Refresh, spec.action)
    }

    @Test
    fun failedCallSaysNoAppCouldStartIt() {
        val spec = UiEvent.CallFailed.toSnackbarSpec(resources)
        assertEquals("No app could start this call", spec.message)
        assertNull(spec.action)
    }

    @Test
    fun refusedCallPermissionNamesTheApp() {
        val spec = UiEvent.CallPermissionDenied("Signal", permanently = false).toSnackbarSpec(resources)
        assertEquals("Calling through Signal needs permission to make phone calls", spec.message)
        assertNull(spec.action)
    }

    @Test
    fun permanentlyRefusedCallPermissionOffersSettings() {
        val spec = UiEvent.CallPermissionDenied("Signal", permanently = true).toSnackbarSpec(resources)
        assertEquals("Calling through Signal needs permission to make phone calls", spec.message)
        assertEquals("Settings", spec.actionLabel)
        assertEquals(UiAction.OpenAppSettings, spec.action)
        assertEquals(SnackbarDuration.Long, spec.duration)
    }

    @Test
    fun aChatOpenedInsteadOfTheCallSaysWhyAndOffersSettings() {
        val spec = UiEvent.CallOpenedChat("Signal").toSnackbarSpec(resources)
        assertEquals(
            "Opened the chat in Signal instead. To call from TidyDial, allow phone calls in settings.",
            spec.message,
        )
        assertEquals("Settings", spec.actionLabel)
        assertEquals(UiAction.OpenAppSettings, spec.action)
        assertEquals(SnackbarDuration.Long, spec.duration)
    }
}
