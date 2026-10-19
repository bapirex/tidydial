package org.tidydial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.tidydial.ui.theme.TidyDialTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppLogoScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appLogo() {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppLogo(size = 112.dp)
                    AppLogo(size = 112.dp, shape = AppLogoDefaults.expressiveShape)
                    AppLogo(size = 64.dp)
                    AppLogo(size = 40.dp)
                }
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/app_logo.png")
    }
}
