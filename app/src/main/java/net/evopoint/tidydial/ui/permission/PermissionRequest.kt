package net.evopoint.tidydial.ui.permission

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
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
 * no handling, since calling through an app that needs it asks again then. A refusal is kept in
 * [PermissionRefusals], so a later request can tell when Android stopped asking.
 */
@Composable
internal fun rememberPermissionRequest(onResult: (UiAction.PermissionResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val refusals = rememberPermissionRefusals()
    // Saved, so asking again for contacts after a refusal does not ask for calls again.
    var callRefused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        when (result[Manifest.permission.CALL_PHONE]) {
            false -> {
                callRefused = true
                refusals.markRefused(Manifest.permission.CALL_PHONE)
            }

            true -> refusals.forget(Manifest.permission.CALL_PHONE)
            null -> Unit
        }
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

/**
 * Whether a refused request means Android no longer shows the dialog. After a refusal it shows
 * the dialog again only while a rationale is expected; but on Android 11+ closing the dialog with
 * Back or a tap outside also comes back refused with no rationale expected, exactly like a first
 * request whose dialog was not shown. So a refusal only counts as permanent when a rationale was
 * expected before asking (the user had refused once), or the permission was refused here before.
 * A dialog closed twice in a row also counts then; the next request still shows it if it can.
 */
internal fun isPermanentRefusal(rationaleBefore: Boolean, rationaleAfter: Boolean, refusedBefore: Boolean): Boolean =
    !rationaleAfter && (rationaleBefore || refusedBefore)

/**
 * Which permissions were refused before, across launches, for [isPermanentRefusal]. Forgotten
 * once granted, since Android asks afresh after it removes the permissions of an unused app. Kept
 * out of Android's backups, as permissions do not carry over to a new install either.
 */
internal class PermissionRefusals(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun wasRefused(permission: String): Boolean = prefs.getBoolean(permission, false)

    fun markRefused(permission: String) = prefs.edit { putBoolean(permission, true) }

    fun forget(permission: String) {
        if (wasRefused(permission)) prefs.edit { remove(permission) }
    }

    /**
     * Records a refused request for [permission] and tells whether it was permanent;
     * [rationaleBefore] is what [Activity.shouldShowRequestPermissionRationale] said before asking.
     */
    fun onRefused(activity: Activity?, permission: String, rationaleBefore: Boolean): Boolean {
        val permanent = activity != null && isPermanentRefusal(
            rationaleBefore = rationaleBefore,
            rationaleAfter = activity.shouldShowRequestPermissionRationale(permission),
            refusedBefore = wasRefused(permission),
        )
        markRefused(permission)
        return permanent
    }

    internal companion object {
        /** Also named in the backup rules. */
        const val FILE = "permission_refusals"
    }
}

@Composable
internal fun rememberPermissionRefusals(): PermissionRefusals {
    val context = LocalContext.current.applicationContext
    return remember(context) { PermissionRefusals(context) }
}

/** What [Activity.shouldShowRequestPermissionRationale] says now; false without an activity. */
internal fun Activity?.expectsRationale(permission: String): Boolean =
    this?.shouldShowRequestPermissionRationale(permission) ?: false

/**
 * The app's page in the system settings, where permissions refused for good can be granted. Does
 * nothing where no app opens it (some locked-down devices disable the settings app): there is
 * nothing else to offer, and a button must never crash the app.
 */
internal fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("PermissionRequest", "No app opens the app's settings", e)
    }
}
