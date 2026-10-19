package org.tidydial.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tidydial.R
import org.tidydial.ui.theme.TidyDialTheme

/** Adaptive icons are 108dp; launchers show the inner 72dp. */
private const val ADAPTIVE_ICON_SCALE = 108f / 72f

/**
 * The launcher icon drawn in Compose: the adaptive background and foreground layers, scaled like
 * a launcher does (the inner 72 of 108 units visible) and clipped to [shape].
 *
 * Decorative by default; pass [contentDescription] when the logo stands alone.
 */
@Composable
fun AppLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = AppLogoDefaults.shape,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticsModifier)
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val layerSize = size * ADAPTIVE_ICON_SCALE
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier.requiredSize(layerSize),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(layerSize),
        )
    }
}

object AppLogoDefaults {
    /** A squircle-like rounded square, close to the most common launcher mask. */
    val shape: Shape = RoundedCornerShape(percent = 30)

    /** Expressive alternative for hero placements, such as the permission screen. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val expressiveShape: Shape
        @Composable get() = MaterialShapes.Cookie9Sided.toShape()
}

@Preview(showBackground = true)
@Composable
private fun AppLogoPreview() {
    TidyDialTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(size = 96.dp)
            AppLogo(size = 96.dp, shape = AppLogoDefaults.expressiveShape)
            AppLogo(size = 48.dp)
            AppLogo(size = 24.dp)
        }
    }
}
