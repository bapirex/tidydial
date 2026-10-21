package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FormatOptionsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var draft by mutableStateOf(SampleData.config)
    private var pickCountryClicks = 0

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun setContent() {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                FormatOptionsContent(
                    draft = draft,
                    onDraftChange = { draft = it },
                    country = SampleData.countries.firstOrNull { it.isoCode == draft.countryIso },
                    detectedCountryIso = "US",
                    accounts = SampleData.accounts,
                    exampleNumber = { region, style -> "$region ${style.name}" },
                    onPickCountry = { pickCountryClicks++ },
                    onApply = {},
                    onCancel = {},
                )
            }
        }
    }

    @Test
    fun formatStylesAreRadioItems() {
        setContent()
        composeRule.onNodeWithText("International", useUnmergedTree = false)
            .assert(hasRole(Role.RadioButton))
            .assertIsSelected()

        composeRule.onNodeWithText("National").performScrollTo().performClick()

        assertEquals(NumberFormatStyle.NATIONAL, draft.formatStyle)
        composeRule.onNodeWithText("National").assertIsSelected()
    }

    @Test
    fun liveExampleFollowsCountry() {
        setContent()
        composeRule.onNodeWithText("US E164", substring = true).assertExists()

        draft = draft.copy(countryIso = "DE")

        composeRule.onNodeWithText("DE E164", substring = true).assertExists()
        composeRule.onNodeWithText("US E164", substring = true).assertDoesNotExist()
    }

    @Test
    fun countryRowOpensPicker() {
        setContent()
        composeRule.onNodeWithText("United States", substring = true).performClick()
        assertEquals(1, pickCountryClicks)
    }

    @Test
    fun extensionDelimiterButtonsSelectOne() {
        setContent()
        composeRule.onNodeWithText("Pause", substring = true).performScrollTo().performClick()
        assertEquals(ExtensionDelimiter.COMMA, draft.extensionDelimiter)
    }

    @Test
    fun shortNumbersRowIsOneSwitch() {
        setContent()
        val row = composeRule.onNodeWithText("Keep short", substring = true).performScrollTo()
        row.assert(hasRole(Role.Switch)).assertIsOn()

        row.performClick()

        assertEquals(false, draft.preserveShortNumbers)
        row.assertIsOff()
    }

    @Test
    fun accountSwitchesToggleExclusion() {
        setContent()
        composeRule.onNodeWithText("WhatsApp", substring = true).performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Google", substring = true).performScrollTo().assertIsOff().performClick()

        assertEquals(
            FormatConfig.DEFAULT_EXCLUDED_ACCOUNT_TYPES + SampleData.GOOGLE,
            draft.excludedAccountTypes,
        )
    }

    @Test
    fun saverRoundTrips() {
        val config = FormatConfig(
            countryIso = "GB",
            formatStyle = NumberFormatStyle.RFC3966,
            extensionDelimiter = ExtensionDelimiter.KEEP_ORIGINAL,
            preserveShortNumbers = false,
            excludedAccountTypes = setOf("com.google", ""),
        )
        val saved = with(FormatConfigSaver) { SaverScope { true }.save(config) }!!
        assertEquals(config, FormatConfigSaver.restore(saved))
    }

    @Test
    fun countryPickerSearchesAndSelects() {
        val countries = listOf(Country("DE", "Germany", 49), Country("FR", "France", 33), Country("US", "United States", 1))
        val search = TextFieldState()
        var picked: Country? = null
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                CountryPickerContent(
                    countries = countries,
                    selectedIso = "US",
                    detectedIso = "US",
                    onSelect = { picked = it },
                    searchState = search,
                )
            }
        }
        composeRule.onNodeWithText("Detected").assertExists()

        search.edit { replace(0, length, "+33") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Detected").assertDoesNotExist()
        composeRule.onNodeWithText("Germany").assertDoesNotExist()
        composeRule.onNodeWithText("France").performClick()
        assertEquals("FR", picked?.isoCode)
    }
}
