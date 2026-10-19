package org.tidydial.ui

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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.tidydial.ui.contacts.ContactsScreen
import org.tidydial.ui.permission.PermissionScreen

private val CONTACT_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
)

private enum class Route { Blank, Permission, Contacts }

/**
 * Root composable: owns the contacts permission flow and switches between the permission
 * explainer and the contacts list.
 */
@Composable
fun TidyDialApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = CONTACT_PERMISSIONS.all { result[it] == true || context.hasPermission(it) }
        viewModel.onAction(
            UiAction.PermissionResult(granted = granted, canAskAgain = activity.canAskAgain()),
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAction(UiAction.PermissionRechecked(context.hasContactsPermission()))
    }

    val route = when (state.permission) {
        PermissionStatus.Unknown -> Route.Blank
        PermissionStatus.Granted -> Route.Contacts
        PermissionStatus.Denied, PermissionStatus.PermanentlyDenied -> Route.Permission
    }
    val motion = MaterialTheme.motionScheme

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "route",
        ) { target ->
            when (target) {
                Route.Blank -> Box(Modifier.fillMaxSize())
                Route.Permission -> PermissionScreen(
                    status = state.permission,
                    onRequest = { permissionLauncher.launch(CONTACT_PERMISSIONS) },
                    onOpenSettings = { context.openAppSettings() },
                )

                Route.Contacts -> ContactsScreen(
                    state = state,
                    events = viewModel.events,
                    onAction = viewModel::onAction,
                    exampleNumber = viewModel::exampleNumber,
                )
            }
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasContactsPermission(): Boolean = CONTACT_PERMISSIONS.all { hasPermission(it) }

/** After a denial, the system only shows the dialog again while a rationale is expected. */
private fun Activity?.canAskAgain(): Boolean =
    this == null || CONTACT_PERMISSIONS.any { shouldShowRequestPermissionRationale(it) }

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
