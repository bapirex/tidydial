package net.evopoint.tidydial.ui.permission

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.PermissionStatus
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    // Google Play phone screenshots: 9:16 (1233x2193 px), copied to fastlane phoneScreenshots.
    @Test
    @Config(qualifiers = "w411dp-h731dp-xxhdpi")
    fun store() {
        show()
        // Scroll both permission items fully into view above the pinned actions.
        val firstPromise = RuntimeEnvironment.getApplication().getString(R.string.permission_promise_offline)
        composeRule.onNodeWithText(firstPromise).performScrollTo()
        capture("store_permission")
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
        // With a font this large the actions scroll with the content instead of staying pinned.
        val footnote = RuntimeEnvironment.getApplication().getString(R.string.permission_footnote)
        composeRule.onNodeWithText(footnote).performScrollTo()
        capture("permission_font_1_5_bottom")
    }

    // A short portrait window, such as one half of a split screen: the actions scroll into view.
    @Test
    @Config(qualifiers = "w360dp-h400dp-xxhdpi")
    fun shortWindowPermanentlyDenied() {
        RuntimeEnvironment.setFontScale(1.5f)
        show(PermissionStatus.PermanentlyDenied)
        val footnote = RuntimeEnvironment.getApplication().getString(R.string.permission_footnote)
        composeRule.onNodeWithText(footnote).performScrollTo()
        capture("permission_short_window_bottom")
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
