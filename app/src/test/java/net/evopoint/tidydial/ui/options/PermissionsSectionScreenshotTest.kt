package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.ui.permission.AccessState
import net.evopoint.tidydial.ui.permission.PermissionsUi
import net.evopoint.tidydial.ui.permission.UnusedAppRestrictions
import net.evopoint.tidydial.ui.preview.SampleData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Permissions section of the format options on its own, in each of its states. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h640dp-xxhdpi")
class PermissionsSectionScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Calls declined; Android may remove permissions if unused. */
    @Test
    fun permissions() = capture("permissions_section")

    /** Everything allowed, on a phone that never removes permissions: no row about it. */
    @Test
    fun permissionsAllAllowed() = capture("permissions_section_all_allowed", permissions = PermissionsUi())

    /** The system dialog no longer appears, and removal of unused apps' permissions is off. */
    @Test
    @Config(qualifiers = "w411dp-h640dp-night-xxhdpi")
    fun permissionsPermanentlyDeniedDark() = capture(
        name = "permissions_section_permanently_denied_dark",
        darkTheme = true,
        permissions = PermissionsUi(
            contacts = AccessState.PermanentlyDenied,
            calls = AccessState.PermanentlyDenied,
            unusedAppRestrictions = UnusedAppRestrictions.Off,
        ),
    )

    @Test
    @Config(qualifiers = "w411dp-h640dp-night-xxhdpi")
    fun permissionsDark() = capture("permissions_section_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    fun permissionsFontScale() = capture("permissions_section_font_1_5", fontScale = 1.5f)

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        permissions: PermissionsUi = SampleData.permissionsUi,
    ) {
        composeRule.setContent {
            SheetFrame(darkTheme = darkTheme, fontScale = fontScale) {
                PermissionsSection(permissions = permissions, onAction = {}, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }
}
