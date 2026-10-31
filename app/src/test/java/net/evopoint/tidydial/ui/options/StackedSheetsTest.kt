package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import net.evopoint.tidydial.data.PhoneNumberFormatter
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StackedSheetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun sheetContentKeepsTheNormalRipple() {
        var outer: Indication? = null
        var inner: Indication? = null
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                outer = LocalIndication.current
                TidyDialSheet(onDismissRequest = {}) {
                    inner = LocalIndication.current
                    Text("Sheet content")
                }
            }
        }
        composeRule.onNodeWithText("Sheet content").assertExists()
        // Only the library's drag handle wrapper sees the round handle ripple.
        assertSame(outer, inner)
        assertNotSame(DragHandleIndication, inner)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun countryPickerOverFormatOptions() {
        val formatter = PhoneNumberFormatter()
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                FormatOptionsSheet(
                    config = SampleData.config,
                    detectedCountryIso = "US",
                    countries = SampleData.countries,
                    accounts = SampleData.accounts,
                    exampleNumber = formatter::exampleNumber,
                    backup = SampleData.backupUi,
                    onBackupEnabledChange = {},
                    onChooseBackupFolder = {},
                    onRestoreBackup = {},
                    onApply = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("United States").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Choose country").assertExists()
        captureScreenRoboImage(filePath = "src/test/screenshots/country_picker_stacked.png")
    }
}
