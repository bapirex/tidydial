package net.evopoint.tidydial.ui.contacts

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import net.evopoint.tidydial.R
import net.evopoint.tidydial.data.BackupReadError
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiEvent

/** What a [UiEvent] shows as a Snackbar; [action] is sent when its button is pressed. */
data class SnackbarSpec(
    val message: String,
    val actionLabel: String? = null,
    val action: UiAction? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
)

/** Formats an event with plurals; extra parts (skipped, failed) are joined with " · ". */
fun UiEvent.toSnackbarSpec(resources: Resources): SnackbarSpec {
    fun plural(id: Int, count: Int) = resources.getQuantityString(id, count, count)
    fun join(parts: List<String>) = parts.reduce { acc, part ->
        resources.getString(R.string.join_dot, acc, part)
    }
    return when (this) {
        is UiEvent.Updated -> {
            val parts = buildList {
                add(plural(R.plurals.list_snackbar_updated, updated))
                if (skippedChanged > 0) add(plural(R.plurals.list_snackbar_skipped, skippedChanged))
                if (failed > 0) add(plural(R.plurals.list_snackbar_failed, failed))
            }
            if (updated > 0) {
                SnackbarSpec(
                    message = join(parts),
                    actionLabel = resources.getString(R.string.list_snackbar_undo),
                    action = UiAction.UndoLast,
                    duration = SnackbarDuration.Long,
                )
            } else {
                SnackbarSpec(message = join(parts), duration = SnackbarDuration.Long)
            }
        }

        is UiEvent.Undone -> {
            val parts = buildList {
                add(plural(R.plurals.list_snackbar_restored, restored))
                if (skippedChanged > 0) add(plural(R.plurals.list_snackbar_kept, skippedChanged))
                if (failed > 0) add(plural(R.plurals.list_snackbar_failed, failed))
            }
            SnackbarSpec(message = join(parts))
        }

        is UiEvent.RestoredFromBackup -> {
            val parts = buildList {
                add(plural(R.plurals.list_snackbar_restored, restored))
                if (skippedChanged > 0) add(plural(R.plurals.list_snackbar_kept, skippedChanged))
                if (notFound > 0) add(plural(R.plurals.list_snackbar_not_found, notFound))
                if (failed > 0) add(plural(R.plurals.list_snackbar_failed, failed))
                if (backupFailed) add(resources.getString(R.string.list_snackbar_backup_not_saved))
            }
            if (restored > 0) {
                // A restore is journaled like an update, so it can be undone.
                SnackbarSpec(
                    message = join(parts),
                    actionLabel = resources.getString(R.string.list_snackbar_undo),
                    action = UiAction.UndoLast,
                    duration = SnackbarDuration.Long,
                )
            } else {
                SnackbarSpec(message = join(parts), duration = SnackbarDuration.Long)
            }
        }

        is UiEvent.NothingToRestore -> when {
            alreadyRestored == 0 && changedSince == 0 && notFound == 0 ->
                SnackbarSpec(message = resources.getString(R.string.backup_empty))

            // Most likely another phone, or a re-added account: say so rather than "changed".
            alreadyRestored == 0 && changedSince == 0 ->
                SnackbarSpec(message = resources.getString(R.string.backup_none_found), duration = SnackbarDuration.Long)

            else -> {
                val parts = buildList {
                    add(resources.getString(R.string.backup_nothing_to_restore))
                    if (alreadyRestored > 0) add(plural(R.plurals.backup_already_restored, alreadyRestored))
                    if (changedSince > 0) add(plural(R.plurals.list_snackbar_kept, changedSince))
                    if (notFound > 0) add(plural(R.plurals.list_snackbar_not_found, notFound))
                }
                SnackbarSpec(message = join(parts), duration = SnackbarDuration.Long)
            }
        }

        UiEvent.BackupReadFailed -> SnackbarSpec(
            message = resources.getString(R.string.backup_read_failed),
            actionLabel = resources.getString(R.string.list_snackbar_retry),
            // Nothing was written and the selection is unchanged, so the same update starts again.
            action = UiAction.ApplySelected,
            duration = SnackbarDuration.Long,
        )

        is UiEvent.BackupUnreadable -> SnackbarSpec(
            message = resources.getString(
                when (error) {
                    BackupReadError.UNREADABLE -> R.string.backup_unreadable
                    BackupReadError.NOT_A_BACKUP -> R.string.backup_not_tidydial
                    BackupReadError.NEWER_FORMAT -> R.string.backup_newer_format
                },
            ),
        )

        UiEvent.NoFilePicker -> SnackbarSpec(
            message = resources.getString(R.string.backup_no_file_picker),
        )

        UiEvent.LoadFailed -> SnackbarSpec(
            message = resources.getString(R.string.error_load_contacts),
            actionLabel = resources.getString(R.string.list_snackbar_retry),
            action = UiAction.Refresh,
            duration = SnackbarDuration.Long,
        )

        UiEvent.JournalSaveFailed -> SnackbarSpec(
            message = resources.getString(R.string.list_snackbar_journal_failed),
            duration = SnackbarDuration.Long,
        )

        UiEvent.SettingsSaveFailed -> SnackbarSpec(
            message = resources.getString(R.string.options_save_failed),
            duration = SnackbarDuration.Long,
        )

        UiEvent.PermissionRevoked -> SnackbarSpec(
            message = resources.getString(R.string.list_snackbar_permission_revoked),
        )

        UiEvent.CallFailed -> SnackbarSpec(
            message = resources.getString(R.string.list_snackbar_call_failed),
        )

        is UiEvent.CallPermissionDenied -> {
            val message = resources.getString(R.string.list_snackbar_call_permission, app)
            if (permanently) {
                // The system will not ask again; only app settings can grant it.
                SnackbarSpec(
                    message = message,
                    actionLabel = resources.getString(R.string.list_snackbar_settings),
                    action = UiAction.OpenAppSettings,
                    duration = SnackbarDuration.Long,
                )
            } else {
                SnackbarSpec(message = message)
            }
        }

        is UiEvent.CallOpenedChat -> SnackbarSpec(
            message = resources.getString(R.string.list_snackbar_call_opened_chat, app),
            actionLabel = resources.getString(R.string.list_snackbar_settings),
            action = UiAction.OpenAppSettings,
            duration = SnackbarDuration.Long,
        )
    }
}
