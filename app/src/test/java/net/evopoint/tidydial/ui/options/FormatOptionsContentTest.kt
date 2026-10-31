package net.evopoint.tidydial.ui.options

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.model.Country
import net.evopoint.tidydial.model.ExtensionDelimiter
import net.evopoint.tidydial.model.FormatConfig
import net.evopoint.tidydial.model.NumberFormatStyle
import net.evopoint.tidydial.ui.BackupError
import net.evopoint.tidydial.ui.BackupUi
import net.evopoint.tidydial.ui.permission.AccessState
import net.evopoint.tidydial.ui.permission.PermissionAction
import net.evopoint.tidydial.ui.permission.PermissionsUi
import net.evopoint.tidydial.ui.permission.UnusedAppRestrictions
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
    private var backup by mutableStateOf(BackupUi())
    private val backupToggles = mutableListOf<Boolean>()
    private var folderClicks = 0
    private var restoreClicks = 0
    private var permissions by mutableStateOf(PermissionsUi())
    private val permissionActions = mutableListOf<PermissionAction>()

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
                    backup = backup,
                    onBackupEnabledChange = { backupToggles += it },
                    onChooseBackupFolder = { folderClicks++ },
                    onRestoreBackup = { restoreClicks++ },
                    permissions = permissions,
                    onPermissionAction = { permissionActions += it },
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
    fun backupsWithoutAFolderOfferOnlyTheSwitchAndRestore() {
        setContent()
        composeRule.onNodeWithText("No folder chosen").performScrollTo().assertExists()
        composeRule.onNodeWithText("Backup folder").assertDoesNotExist()

        composeRule.onNodeWithText("Back up before each update").performScrollTo()
            .assert(hasRole(Role.Switch))
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithText("Restore from a backup…").performScrollTo().performClick()

        assertEquals(listOf(true), backupToggles)
        assertEquals(1, restoreClicks)
    }

    @Test
    fun backupFolderCanBeChangedAndWarnsWhenUnavailable() {
        backup = BackupUi(enabled = true, folderName = "TidyDial backups")
        setContent()
        composeRule.onNodeWithText("Back up before each update").performScrollTo().assertIsOn()
        composeRule.onNodeWithText("TidyDial backups").performScrollTo().performClick()
        assertEquals(1, folderClicks)
        // Backups are not part of the draft: nothing to apply.
        assertEquals(SampleData.config, draft)

        backup = backup.copy(folderAvailable = false)
        composeRule.onNodeWithText("Can’t write here anymore. Choose the folder again.").assertExists()
        composeRule.onNodeWithText("Back up before each update").performClick()
        assertEquals(listOf(false), backupToggles)
    }

    @Test
    fun backupFolderRowSaysWhatItChangesToScreenReaders() {
        backup = BackupUi(enabled = true, folderName = "TidyDial backups")
        setContent()
        val row = composeRule.onNode(hasText("TidyDial backups") and hasClickAction(), useUnmergedTree = false)
        row.performScrollTo().assert(hasText("Backup folder"))
        assertEquals(
            "Change backup folder",
            row.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        row.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, folderClicks)
    }

    @Test
    fun backupSettingsSayTheyAreSavedRightAwayAndShowErrors() {
        backup = BackupUi(enabled = true, folderName = "TidyDial backups", error = BackupError.SAVE_FAILED)
        setContent()
        composeRule.onNode(
            hasText("TidyDial saves the old and new numbers", substring = true) and
                hasText("Changes here are saved right away", substring = true),
        ).performScrollTo()
        composeRule.onNodeWithText("Couldn’t save your backup choice. Try again.").performScrollTo().assertExists()
        backup = backup.copy(error = BackupError.NO_PICKER)
        composeRule.onNodeWithText("No app found to pick a folder").assertExists()
    }

    @Test
    fun permissionsSayTheyAreSavedRightAwayAndReportTheirRows() {
        permissions = PermissionsUi(calls = AccessState.Denied, unusedAppRestrictions = UnusedAppRestrictions.On)
        setContent()
        composeRule.onNodeWithText("Permissions").performScrollTo()
        // Worded like the Backups section, which follows the same rule.
        composeRule.onNode(
            hasText("Allow a permission you declined", substring = true) and
                hasText("Changes here are saved right away, without Apply.", substring = true),
        ).performScrollTo()

        composeRule.onNodeWithText("Allow").performScrollTo().performClick()
        composeRule.onNodeWithText("Turn off").performScrollTo().performClick()

        assertEquals(
            listOf(PermissionAction.AllowCalls, PermissionAction.ManageUnusedAppRestrictions),
            permissionActions,
        )
        // Permissions are not part of the draft: nothing to apply.
        assertEquals(SampleData.config, draft)
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
