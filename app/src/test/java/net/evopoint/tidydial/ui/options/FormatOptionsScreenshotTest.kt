package net.evopoint.tidydial.ui.options

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.ui.preview.SampleData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FormatOptionsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun formatOptions() = capture("format_options")

    // Google Play phone screenshots: 9:16 (1233x2193 px), copied to fastlane phoneScreenshots.
    @Test
    @Config(qualifiers = "w411dp-h731dp-xxhdpi")
    fun store() = capture("store_format_options")

    @Test
    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    fun formatOptionsFull() = capture("format_options_full")

    @Test
    @Config(qualifiers = "w411dp-h1900dp-night-xxhdpi")
    fun formatOptionsFullDark() = capture("format_options_full_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    fun formatOptionsFullFontScale() = capture("format_options_full_font_1_5", fontScale = 1.5f)

    @Test
    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    fun formatOptionsForeignCountryNoAccounts() = capture(
        name = "format_options_foreign_no_accounts",
        config = FormatConfig(
            countryIso = "DE",
            formatStyle = NumberFormatStyle.NATIONAL,
            extensionDelimiter = ExtensionDelimiter.KEEP_ORIGINAL,
            preserveShortNumbers = false,
        ),
        withAccounts = false,
    )

    private val formatter = PhoneNumberFormatter()

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        config: FormatConfig = SampleData.config,
        withAccounts: Boolean = true,
    ) {
        composeRule.setContent {
            SheetFrame(darkTheme = darkTheme, fontScale = fontScale) {
                Content(config, withAccounts)
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    @Composable
    private fun Content(config: FormatConfig, withAccounts: Boolean) {
        FormatOptionsContent(
            draft = config,
            onDraftChange = {},
            country = SampleData.countries.firstOrNull { it.isoCode == config.countryIso },
            detectedCountryIso = "US",
            accounts = if (withAccounts) SampleData.accounts else emptyList(),
            exampleNumber = formatter::exampleNumber,
            onPickCountry = {},
            onApply = {},
            onCancel = {},
        )
    }
}
