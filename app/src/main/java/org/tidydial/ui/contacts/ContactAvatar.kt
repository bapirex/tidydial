package org.tidydial.ui.contacts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import org.tidydial.R
import org.tidydial.ui.theme.TidyDialTheme

/** How many of a contact's selectable numbers are selected. */
enum class AvatarSelection { None, Partial, All }

private data class AvatarColors(val container: Color, val content: Color)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val AvatarPolygons: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.Flower,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Gem,
        MaterialShapes.Pentagon,
        MaterialShapes.Puffy,
        MaterialShapes.Cookie4Sided,
    )
}

/** MurmurHash3's 64-bit finalizer: neighbouring ids get unrelated shapes and colors. */
private fun stableHash(id: Long): Int {
    var h = id
    h = h xor (h ushr 33)
    h *= -0xae502812aa7333L
    h = h xor (h ushr 33)
    h *= -0x3b314601e57a13adL
    h = h xor (h ushr 33)
    return (h ushr 1).toInt() and Int.MAX_VALUE
}

private fun ColorScheme.avatarColors(index: Int): AvatarColors = when (index % 3) {
    0 -> AvatarColors(primaryContainer, onPrimaryContainer)
    1 -> AvatarColors(secondaryContainer, onSecondaryContainer)
    else -> AvatarColors(tertiaryContainer, onTertiaryContainer)
}

/**
 * Initials inside an Expressive [MaterialShapes] shape; shape and tonal colors are picked by a
 * stable hash of [contactId]. When [onToggle] is set the avatar selects or clears all of the
 * contact's numbers, and turns into a check once they are all selected.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContactAvatar(
    contactId: Long,
    name: String,
    initials: String,
    selection: AvatarSelection,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val hash = stableHash(contactId)
    val shape = AvatarPolygons[hash % AvatarPolygons.size].toShape()
    val scheme = MaterialTheme.colorScheme
    val idle = scheme.avatarColors(hash / AvatarPolygons.size)
    val checked = selection == AvatarSelection.All
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (checked) scheme.primary else idle.container,
        animationSpec = motion.defaultEffectsSpec(),
        label = "avatarContainer",
    )
    val content by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else idle.content,
        animationSpec = motion.defaultEffectsSpec(),
        label = "avatarContent",
    )
    val actionLabel = if (checked) {
        stringResource(R.string.list_avatar_deselect, name)
    } else {
        stringResource(R.string.list_avatar_select, name)
    }
    val interaction = if (onToggle != null) {
        Modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = actionLabel
                role = Role.Button
                onClick { onToggle(); true }
            }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), 0.6f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(targetScale = 0.6f))
            },
            label = "avatarContent",
        ) { isChecked ->
            if (isChecked) {
                Icon(CheckIcon, contentDescription = null, tint = content)
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactAvatarPreview() {
    TidyDialTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (1L..6L).forEach { id ->
                ContactAvatar(
                    contactId = id,
                    name = "Sample $id",
                    initials = "S$id",
                    selection = if (id == 6L) AvatarSelection.All else AvatarSelection.None,
                    onToggle = {},
                )
            }
        }
    }
}
