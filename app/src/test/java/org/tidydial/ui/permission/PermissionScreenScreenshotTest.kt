package org.tidydial.ui.permission

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.tidydial.R
import org.tidydial.ui.PermissionStatus
import org.tidydial.ui.theme.TidyDialTheme

private const val PHONE = "w411dp-h891dp-xxhdpi"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class PermissionScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(status: PermissionStatus = PermissionStatus.Denied) {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                PermissionScreen(status = status, onRequest = {}, onOpenSettings = {})
            }
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Test
    fun light() {
        show()
        capture("permission_light")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun dark() {
        show()
        capture("permission_dark")
    }

    @Test
    fun permanentlyDenied() {
        show(PermissionStatus.PermanentlyDenied)
        capture("permission_permanently_denied")
    }

    @Test
    fun largeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        show()
        capture("permission_font_1_5")
        val footnote = RuntimeEnvironment.getApplication().getString(R.string.permission_footnote)
        composeRule.onNodeWithText(footnote).performScrollTo()
        capture("permission_font_1_5_bottom")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() {
        show()
        capture("permission_landscape")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-night-xxhdpi")
    fun landscapePermanentlyDenied() {
        show(PermissionStatus.PermanentlyDenied)
        capture("permission_landscape_permanently_denied")
    }
}
