package net.evopoint.tidydial.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.evopoint.tidydial.ui.UiAction

/** Contacts access, which TidyDial cannot work without. */
internal val ContactPermissions = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
)

internal fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasContactsPermission(): Boolean = ContactPermissions.all { hasPermission(it) }

/**
 * Returns the action behind the explainer's button: asks for contacts access and, in the same
 * step, for the optional call permission (CALL_PHONE) unless it is granted already or was refused
 * here before. Reports only the contacts answer with [onResult]: a refused call permission needs
 * no handling, since calling through an app that needs it asks again then.
 */
@Composable
internal fun rememberPermissionRequest(onResult: (UiAction.PermissionResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    // Saved, so asking again for contacts after a refusal does not ask for calls again.
    var callRefused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.CALL_PHONE] == false) callRefused = true
        val granted = ContactPermissions.all { result[it] == true || context.hasPermission(it) }
        currentOnResult(
            UiAction.PermissionResult(granted = granted, canAskAgain = activity.canAskAgain(ContactPermissions)),
        )
    }
    return {
        val askForCalls = !callRefused && !context.hasPermission(Manifest.permission.CALL_PHONE)
        launcher.launch(if (askForCalls) ContactPermissions + Manifest.permission.CALL_PHONE else ContactPermissions)
    }
}

/** After a denial, the system only shows the dialog again while a rationale is expected. */
internal fun Activity?.canAskAgain(permissions: Array<String>): Boolean =
    this == null || permissions.any { shouldShowRequestPermissionRationale(it) }

/** The app's page in the system settings, where permissions refused for good can be granted. */
internal fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
