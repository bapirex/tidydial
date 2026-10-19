package org.tidydial.ui.contacts

import android.content.res.Resources
import androidx.compose.material3.SnackbarDuration
import org.tidydial.R
import org.tidydial.ui.UiAction
import org.tidydial.ui.UiEvent

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
    }
}
