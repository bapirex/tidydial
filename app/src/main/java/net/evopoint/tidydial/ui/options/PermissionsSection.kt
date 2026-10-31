package net.evopoint.tidydial.ui.options

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.permission.AccessState
import net.evopoint.tidydial.ui.permission.PermissionAction
import net.evopoint.tidydial.ui.permission.PermissionsUi
import net.evopoint.tidydial.ui.permission.UnusedAppRestrictions
import net.evopoint.tidydial.ui.theme.TidyDialTheme

/**
 * What Android currently lets TidyDial do: contacts access (required), the call permission
 * (optional) and, where Android removes permissions of apps left unused for months, whether it
 * may do so for TidyDial. Each row that is not as it should be can be tapped, which reports a
 * [PermissionAction]. Like the Backups section it applies right away, without Apply.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PermissionsSection(
    permissions: PermissionsUi,
    onAction: (PermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val restrictions = permissions.unusedAppRestrictions
    val count = if (restrictions != null) 3 else 2
    SheetSection(
        title = stringResource(R.string.options_section_permissions),
        description = stringResource(R.string.options_permissions_summary),
        modifier = modifier,
    ) {
        PermissionRow(
            index = 0,
            count = count,
            icon = R.drawable.ic_contacts,
            title = R.string.permission_contacts_title,
            required = true,
            access = permissions.contacts,
            allow = PermissionAction.AllowContacts,
            allowLabel = R.string.options_permissions_allow_contacts_action,
            settingsLabel = R.string.options_permissions_contacts_settings_action,
            onAction = onAction,
        )
        PermissionRow(
            index = 1,
            count = count,
            icon = R.drawable.ic_call,
            title = R.string.permission_call_title,
            required = false,
            access = permissions.calls,
            allow = PermissionAction.AllowCalls,
            allowLabel = R.string.options_permissions_allow_calls_action,
            settingsLabel = R.string.options_permissions_calls_settings_action,
            onAction = onAction,
        )
        if (restrictions != null) {
            UnusedAppRestrictionsRow(index = 2, count = count, restrictions = restrictions, onAction = onAction)
        }
    }
}

/**
 * One permission and its state. Not allowed, the row offers "Allow" while the system still shows
 * its dialog, and "Open settings" once it does not, saying why. Required permissions show a
 * missing grant in the error color.
 */
@Composable
private fun PermissionRow(
    index: Int,
    count: Int,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    required: Boolean,
    access: AccessState,
    allow: PermissionAction,
    @StringRes allowLabel: Int,
    @StringRes settingsLabel: Int,
    onAction: (PermissionAction) -> Unit,
) {
    val allowed = access == AccessState.Allowed
    // "Required · Allowed", "Optional · Not allowed".
    val state = stringResource(
        R.string.join_dot,
        stringResource(if (required) R.string.permission_required else R.string.permission_optional),
        stringResource(if (allowed) R.string.options_permission_allowed else R.string.options_permission_not_allowed),
    )
    ActionRow(
        index = index,
        count = count,
        icon = icon,
        title = stringResource(title),
        action = when (access) {
            AccessState.Allowed -> null
            AccessState.Denied -> RowAction(R.string.options_permission_allow, allowLabel) { onAction(allow) }
            AccessState.PermanentlyDenied ->
                RowAction(R.string.permission_open_settings, settingsLabel) { onAction(PermissionAction.OpenAppSettings) }
        },
    ) {
        Text(
            text = state,
            color = if (!allowed && required) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (access == AccessState.PermanentlyDenied) {
            Text(stringResource(R.string.options_permission_permanently_denied))
        }
    }
}

/** Whether Android may remove TidyDial's permissions after a few months without use. */
@Composable
private fun UnusedAppRestrictionsRow(
    index: Int,
    count: Int,
    restrictions: UnusedAppRestrictions,
    onAction: (PermissionAction) -> Unit,
) {
    ActionRow(
        index = index,
        count = count,
        icon = R.drawable.ic_lock,
        title = stringResource(R.string.options_permissions_unused),
        action = when (restrictions) {
            UnusedAppRestrictions.On -> RowAction(
                R.string.options_permissions_unused_turn_off,
                R.string.options_permissions_unused_turn_off_action,
            ) { onAction(PermissionAction.ManageUnusedAppRestrictions) }
            UnusedAppRestrictions.Off -> null
        },
    ) {
        Text(
            stringResource(
                when (restrictions) {
                    UnusedAppRestrictions.On -> R.string.options_permissions_unused_on
                    UnusedAppRestrictions.Off -> R.string.options_permissions_unused_off
                },
            ),
        )
    }
}

/**
 * What tapping a row does: [text] shows on the row, [actionLabel] tells screen readers what it
 * does, for example "Double-tap to allow phone calls" rather than a bare "Double-tap to activate".
 */
private class RowAction(@StringRes val text: Int, @StringRes val actionLabel: Int, val onClick: () -> Unit)

/**
 * A row of the section. With an [action], the whole row is the tap target, like the Backups rows,
 * and [action]'s text shows as a pill: at the end of the row, or under the text at large font
 * sizes, where the end of the row would squeeze the text into a narrow column. Its text is a
 * polite live region, so a screen reader reads out a change, such as Android refusing to show its
 * dialog again after "Allow".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionRow(
    index: Int,
    count: Int,
    @DrawableRes icon: Int,
    title: String,
    action: RowAction?,
    supporting: @Composable ColumnScope.() -> Unit,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val leading: @Composable () -> Unit = { Icon(painterResource(icon), contentDescription = null) }
    if (action == null) {
        SegmentedListItem(
            shapes = shapes,
            colors = sheetListItemColors(),
            leadingContent = leading,
            supportingContent = { Column(content = supporting) },
        ) {
            Text(title)
        }
        return
    }
    val pillBelow = LocalDensity.current.fontScale > LargeFontScale
    val label = stringResource(action.actionLabel)
    SegmentedListItem(
        onClick = action.onClick,
        shapes = shapes,
        colors = sheetListItemColors(),
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            onClick(label = label) {
                action.onClick()
                true
            }
        },
        leadingContent = leading,
        supportingContent = {
            Column {
                supporting()
                if (pillBelow) ActionPill(action.text, Modifier.padding(top = 8.dp))
            }
        },
        trailingContent = if (pillBelow) null else { { ActionPill(action.text) } },
    ) {
        Text(title)
    }
}

/** Font scale from which a row's pill moves under its text. */
private const val LargeFontScale = 1.3f

/** Looks like a tonal button; the row around it takes the taps. */
@Composable
private fun ActionPill(@StringRes text: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsSectionPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionsSection(
            permissions = PermissionsUi(
                calls = AccessState.Denied,
                unusedAppRestrictions = UnusedAppRestrictions.On,
            ),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsSectionBlockedPreview() {
    TidyDialTheme(darkTheme = true, dynamicColor = false) {
        PermissionsSection(
            permissions = PermissionsUi(
                contacts = AccessState.Denied,
                calls = AccessState.PermanentlyDenied,
                unusedAppRestrictions = UnusedAppRestrictions.Off,
            ),
            onAction = {},
        )
    }
}
