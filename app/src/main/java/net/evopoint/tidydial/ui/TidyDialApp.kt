package net.evopoint.tidydial.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import net.evopoint.tidydial.ui.contacts.ContactsScreen
import net.evopoint.tidydial.ui.permission.PermissionScreen
import net.evopoint.tidydial.ui.permission.hasContactsPermission
import net.evopoint.tidydial.ui.permission.rememberPermissionRequest

private enum class Route { Blank, Permission, Contacts }

/**
 * Root composable: owns the permission flow and switches between the permission explainer and
 * the contacts list. The explainer asks for contacts access and the optional call permission
 * together; the contacts screen asks for the call permission again only when a call needs it and
 * it was not granted.
 */
@Composable
fun TidyDialApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPermissions = rememberPermissionRequest(onResult = viewModel::onAction)

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
                    onRequest = requestPermissions,
                    onOpenSettings = { context.openAppSettings() },
                )

                Route.Contacts -> ContactsScreen(
                    state = state,
                    events = viewModel.events,
                    onAction = { action ->
                        when (action) {
                            // From the Snackbar after the call permission was permanently denied.
                            UiAction.OpenAppSettings -> context.openAppSettings()
                            else -> viewModel.onAction(action)
                        }
                    },
                    exampleNumber = viewModel::exampleNumber,
                )
            }
        }
    }
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
