package org.tidydial.ui.about

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.tidydial.ui.options.SheetFrame

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AboutScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun about() = capture("about")

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun aboutDark() = capture("about_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w411dp-h1950dp-xxhdpi")
    fun aboutExpandedFontScale() = capture("about_expanded_font_1_5", fontScale = 1.5f, expanded = true)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun aboutExpanded() = capture("about_expanded", expanded = true)

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        expanded: Boolean = false,
    ) {
        composeRule.setContent {
            SheetFrame(darkTheme = darkTheme, fontScale = fontScale) {
                AboutContent(
                    versionName = "1.0.0",
                    versionCode = 1,
                    onOpenSourceCode = {},
                    initiallyExpanded = expanded,
                )
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }
}
