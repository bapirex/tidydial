package net.evopoint.tidydial.ui.permission

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Whether TidyDial holds a permission, and whether Android still shows its dialog. */
enum class AccessState {
    Allowed,
    Denied,

    /** Refused for good: the system no longer shows the dialog, only the app's settings help. */
    PermanentlyDenied,
}

/** Whether Android removes TidyDial's permissions after a few months without use. */
enum class UnusedAppRestrictions { On, Off }

/** What the Permissions section of the format options shows. */
@Immutable
data class PermissionsUi(
    val contacts: AccessState = AccessState.Allowed,
    val calls: AccessState = AccessState.Allowed,
    /** Null where Android never removes permissions of unused apps, or cannot tell. */
    val unusedAppRestrictions: UnusedAppRestrictions? = null,
)

/** The buttons of the Permissions section. */
enum class PermissionAction { AllowContacts, AllowCalls, OpenAppSettings, ManageUnusedAppRestrictions }

/** Reads [UnusedAppRestrictions]; faked in tests so they do not depend on the system. */
fun interface UnusedAppRestrictionsSource {
    suspend fun status(): UnusedAppRestrictions?
}

/**
 * Asks androidx.core: Android 11+ itself, or Google Play on Android 8 to 10 (where it backports
 * the feature). Any failure counts as "cannot tell", which hides the row.
 */
internal class SystemUnusedAppRestrictions(private val context: Context) : UnusedAppRestrictionsSource {
    override suspend fun status(): UnusedAppRestrictions? {
        val status = try {
            PackageManagerCompat.getUnusedAppRestrictionsStatus(context).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return unusedAppRestrictionsOf(status)
    }
}

/** Maps an [UnusedAppRestrictionsConstants] status; null for ERROR and FEATURE_NOT_AVAILABLE. */
internal fun unusedAppRestrictionsOf(status: Int): UnusedAppRestrictions? = when (status) {
    UnusedAppRestrictionsConstants.DISABLED -> UnusedAppRestrictions.Off
    UnusedAppRestrictionsConstants.API_30_BACKPORT,
    UnusedAppRestrictionsConstants.API_30,
    UnusedAppRestrictionsConstants.API_31,
    -> UnusedAppRestrictions.On
    else -> null
}

/**
 * Waits for the future without Guava's or concurrent-futures' coroutine adapters: rethrows the
 * cause of a failure, and cancels the future when the caller is cancelled.
 */
internal suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.resumeWithException((e as? java.util.concurrent.ExecutionException)?.cause ?: e)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}

@Composable
internal fun rememberSystemUnusedAppRestrictions(): UnusedAppRestrictionsSource {
    val context = LocalContext.current.applicationContext
    return remember(context) { SystemUnusedAppRestrictions(context) }
}

/** The Permissions section's state, and the handler for its buttons. */
class PermissionSettings internal constructor(
    val ui: PermissionsUi,
    val onAction: (PermissionAction) -> Unit,
)

/**
 * State and actions for the Permissions section: asks again for contacts access or the call
 * permission, opens the app's settings once the system dialog no longer appears, and opens the
 * system page about removing permissions of unused apps.
 *
 * Rechecks on every resume (back from the system settings or a dialog) and after each answer.
 * Like the setup screen, contacts access counts as permanently denied when a request comes back
 * refused while no rationale is expected, which is also what happens when the dialog was not
 * shown at all; until then "Allow" is offered. The call permission follows [isPermanentRefusal]
 * instead, so a call dialog closed without an answer still offers "Allow". Contacts access lost
 * while the app runs still leads back to the setup screen, through the app's own recheck.
 */
@Composable
internal fun rememberPermissionSettings(
    unusedAppRestrictions: UnusedAppRestrictionsSource = rememberSystemUnusedAppRestrictions(),
): PermissionSettings {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val refusals = rememberPermissionRefusals()
    var contactsGranted by remember { mutableStateOf(context.hasContactsPermission()) }
    var callsGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CALL_PHONE)) }
    // Saved, so "Open settings" survives rotation; a fresh sheet offers "Allow" again first.
    var contactsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var callsPermanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var callsRationaleBefore by rememberSaveable { mutableStateOf(false) }
    var restrictions by remember { mutableStateOf<UnusedAppRestrictions?>(null) }
    var checks by remember { mutableIntStateOf(0) }

    fun recheck() {
        contactsGranted = context.hasContactsPermission()
        callsGranted = context.hasPermission(Manifest.permission.CALL_PHONE)
        if (contactsGranted) contactsPermanentlyDenied = false
        if (callsGranted) {
            callsPermanentlyDenied = false
            refusals.forget(Manifest.permission.CALL_PHONE)
        }
        checks++
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck() }
    LaunchedEffect(unusedAppRestrictions, checks) { restrictions = unusedAppRestrictions.status() }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // An empty answer means the request was cancelled, not refused.
        if (result.isNotEmpty() && !ContactPermissions.all { result[it] == true }) {
            contactsPermanentlyDenied = !activity.canAskAgain(ContactPermissions)
        }
        recheck()
    }
    val callsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.CALL_PHONE] == false) {
            callsPermanentlyDenied =
                refusals.onRefused(activity, Manifest.permission.CALL_PHONE, callsRationaleBefore)
        }
        recheck()
    }
    // The system page expects to be started for a result; coming back rechecks.
    val restrictionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { recheck() }

    fun access(granted: Boolean, permanentlyDenied: Boolean) = when {
        granted -> AccessState.Allowed
        permanentlyDenied -> AccessState.PermanentlyDenied
        else -> AccessState.Denied
    }

    return PermissionSettings(
        ui = PermissionsUi(
            contacts = access(contactsGranted, contactsPermanentlyDenied),
            calls = access(callsGranted, callsPermanentlyDenied),
            unusedAppRestrictions = restrictions,
        ),
        onAction = { action ->
            when (action) {
                PermissionAction.AllowContacts -> contactsLauncher.launch(ContactPermissions)
                PermissionAction.AllowCalls -> {
                    callsRationaleBefore = activity.expectsRationale(Manifest.permission.CALL_PHONE)
                    callsLauncher.launch(CallPermissions)
                }
                PermissionAction.OpenAppSettings -> context.openAppSettings()
                PermissionAction.ManageUnusedAppRestrictions -> {
                    val launched = context.unusedAppRestrictionsIntent()?.let { intent ->
                        try {
                            restrictionsLauncher.launch(intent)
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        }
                    } ?: false
                    // From Android 12 the setting is on the app's info page anyway.
                    if (!launched) context.openAppSettings()
                }
            }
        },
    )
}

private val CallPermissions = arrayOf(Manifest.permission.CALL_PHONE)

/**
 * Null when the feature went away since the row was shown: androidx.core then throws, or on
 * Android 8 to 10 fails its own null check when Google Play's verifier app is gone.
 */
private fun Context.unusedAppRestrictionsIntent(): Intent? = try {
    IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName)
} catch (e: UnsupportedOperationException) {
    null
} catch (e: NullPointerException) {
    null
}

