package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.data.CountryCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CountryPickerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val countries = CountryCatalog().countries(Locale.US)

    @Test
    fun countryPicker() = capture("country_picker")

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun countryPickerDark() = capture("country_picker_dark", darkTheme = true)

    @Test
    fun countryPickerFontScale() = capture("country_picker_font_1_5", fontScale = 1.5f)

    @Test
    fun countryPickerSearch() = capture("country_picker_search", query = "ger")

    @Test
    fun countryPickerNoResults() = capture("country_picker_no_results", query = "Atlantis")

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        query: String = "",
    ) {
        composeRule.setContent {
            SheetFrame(darkTheme = darkTheme, fontScale = fontScale) {
                CountryPickerContent(
                    countries = countries,
                    selectedIso = "DE",
                    detectedIso = "US",
                    onSelect = {},
                    searchState = TextFieldState(query),
                )
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }
}
