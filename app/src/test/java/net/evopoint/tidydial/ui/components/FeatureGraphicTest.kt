package net.evopoint.tidydial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.theme.LightColors
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import net.evopoint.tidydial.ui.theme.phoneNumber
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the Google Play feature graphic (1024 x 500 px; mdpi, so 1dp = 1px). Copy the
 * recorded image to fastlane/metadata/android/en-US/images/featureGraphic.png after changes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1024dp-h500dp-mdpi")
class FeatureGraphicTest {

    @Test
    fun featureGraphic() {
        // The tagline is the store's short description, so the two never drift apart.
        val tagline = File("../fastlane/metadata/android/en-US/short_description.txt").readText().trim()
        captureRoboImage(filePath = "src/test/screenshots/feature_graphic.png") {
            TidyDialTheme(darkTheme = false, dynamicColor = false) {
                FeatureGraphic(tagline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeatureGraphic(tagline: String) {
    val colors = LightColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.primaryContainer),
    ) {
        // Soft Expressive shapes bleeding off the edges, behind everything else.
        Box(
            modifier = Modifier
                .size(520.dp)
                .offset(x = 720.dp, y = 190.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(colors.primaryFixedDim.copy(alpha = 0.45f)),
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = (-70).dp, y = (-80).dp)
                .clip(MaterialShapes.Flower.toShape())
                .background(colors.primaryFixedDim.copy(alpha = 0.35f)),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                AppLogo(size = 212.dp, shape = AppLogoDefaults.expressiveShape)
            }
            Spacer(Modifier.width(64.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "TidyDial",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 76.sp,
                        lineHeight = 84.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp),
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(28.dp))
                BeforeAfterChip()
            }
        }
    }
}

/** The app's core idea in one line: a messy number and its tidy result (fictional digits). */
@Composable
private fun BeforeAfterChip() {
    val colors = LightColors
    Surface(shape = CircleShape, color = colors.surfaceContainerLowest) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "2125550199",
                style = MaterialTheme.typography.titleMedium.phoneNumber()
                    .copy(textDecoration = TextDecoration.LineThrough),
                color = colors.onSurfaceVariant,
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "+1 212-555-0199",
                style = MaterialTheme.typography.titleLarge.phoneNumber()
                    .copy(fontWeight = FontWeight.Medium),
                color = colors.primary,
            )
        }
    }
}
