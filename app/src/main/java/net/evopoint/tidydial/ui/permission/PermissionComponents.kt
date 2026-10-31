package net.evopoint.tidydial.ui.permission

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.PermissionStatus
import net.evopoint.tidydial.ui.components.AppLogo
import net.evopoint.tidydial.ui.components.AppLogoDefaults
import net.evopoint.tidydial.ui.theme.PhoneNumberTextStyles
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import net.evopoint.tidydial.ui.theme.phoneNumber

/** Logo, welcome label, headline and a one-sentence description. */
@Composable
internal fun PermissionHero(
    modifier: Modifier = Modifier,
    logoScale: () -> Float = { 1f },
    logoAlpha: () -> Float = { 1f },
    compact: Boolean = false,
) {
    val typography = MaterialTheme.typography
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(
            size = if (compact) 64.dp else 96.dp,
            shape = AppLogoDefaults.expressiveShape,
            modifier = Modifier.graphicsLayer {
                scaleX = logoScale()
                scaleY = logoScale()
                alpha = logoAlpha()
            },
        )
        if (compact) {
            // Short windows drop the welcome label to keep the button on screen.
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.permission_welcome),
                style = typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(R.string.permission_headline),
            style = if (compact) typography.headlineMediumEmphasized else typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A sample number row showing what TidyDial does: the original, an arrow and the result. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExampleCard(modifier: Modifier = Modifier) {
    val before = stringResource(R.string.permission_example_before)
    val after = stringResource(R.string.permission_example_after)
    val description = stringResource(R.string.permission_example_description, before, after)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialShapes.Cookie4Sided.toShape())
                    .background(colors.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_contacts),
                    contentDescription = null,
                    tint = colors.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.permission_example_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = before,
                        style = PhoneNumberTextStyles.medium,
                        color = colors.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = after,
                            style = MaterialTheme.typography.titleMedium.phoneNumber(),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** One permission TidyDial asks for, and what it is used for. */
private class PermissionUse(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val required: Boolean,
)

private val permissionUses = listOf(
    PermissionUse(
        icon = R.drawable.ic_contacts,
        title = R.string.permission_contacts_title,
        body = R.string.permission_contacts_body,
        required = true,
    ),
    PermissionUse(
        icon = R.drawable.ic_call,
        title = R.string.permission_call_title,
        body = R.string.permission_call_body,
        required = false,
    ),
)

/** Each permission in its own labelled row (required or optional), as one connected group. */
@Composable
internal fun PermissionUses(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        permissionUses.forEachIndexed { index, use ->
            PermissionUseRow(use = use, shape = segmentShape(index = index, count = permissionUses.size))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionUseRow(use: PermissionUse, shape: Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = shape,
        color = colors.surfaceContainer,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(use.icon),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                // The label wraps below the name when a large font leaves no room beside it.
                FlowRow(
                    modifier = Modifier.heightIn(min = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(use.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )
                    PermissionLabel(required = use.required)
                }
                Text(
                    text = stringResource(use.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Required" on a filled pill, "Optional" on an outlined one. */
@Composable
private fun PermissionLabel(required: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (required) colors.secondaryContainer else Color.Transparent,
        contentColor = if (required) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (required) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Text(
            text = stringResource(if (required) R.string.permission_required else R.string.permission_optional),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

private val promises = listOf(
    R.drawable.ic_cloud_off to R.string.permission_promise_offline,
    R.drawable.ic_undo to R.string.permission_promise_undo,
)

/** The privacy promises as one connected group of rows. */
@Composable
internal fun PrivacyPromises(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        promises.forEachIndexed { index, (icon, text) ->
            PromiseRow(
                icon = icon,
                text = text,
                shape = segmentShape(index = index, count = promises.size),
            )
        }
    }
}

private val OuterCorner = 24.dp
private val InnerCorner = 4.dp

private fun segmentShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun PromiseRow(
    @DrawableRes icon: Int,
    @StringRes text: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = shape,
        color = colors.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        }
    }
}

/** Status-dependent explanation, the primary button and a reassuring footnote. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PermissionActions(
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permanentlyDenied = status == PermissionStatus.PermanentlyDenied
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (status) {
            PermissionStatus.PermanentlyDenied -> SettingsNotice()
            PermissionStatus.Denied -> Text(
                text = stringResource(R.string.permission_denied_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            PermissionStatus.Unknown, PermissionStatus.Granted -> Unit
        }
        val height = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = permanentlyDenied),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height),
        ) {
            if (permanentlyDenied) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            }
            Text(
                text = stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_continue,
                ),
                style = ButtonDefaults.textStyleFor(height),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.permission_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsNotice(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_block),
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.permission_permanently_denied),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionHeroPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionHero(modifier = Modifier.padding(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ExampleCardPreview() {
    TidyDialTheme(dynamicColor = false) {
        ExampleCard(modifier = Modifier.padding(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionUsesPreview() {
    TidyDialTheme(dynamicColor = false) {
        PermissionUses(modifier = Modifier.padding(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun PrivacyPromisesPreview() {
    TidyDialTheme(dynamicColor = false) {
        PrivacyPromises(modifier = Modifier.padding(24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionActionsPreview() {
    TidyDialTheme(dynamicColor = false) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            PermissionActions(status = PermissionStatus.Denied, onRequest = {}, onOpenSettings = {})
            PermissionActions(
                status = PermissionStatus.PermanentlyDenied,
                onRequest = {},
                onOpenSettings = {},
            )
        }
    }
}
