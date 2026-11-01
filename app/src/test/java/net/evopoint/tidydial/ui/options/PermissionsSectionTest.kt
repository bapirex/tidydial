package net.evopoint.tidydial.ui.options

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.ui.permission.AccessState
import net.evopoint.tidydial.ui.permission.PermissionAction
import net.evopoint.tidydial.ui.permission.PermissionsUi
import net.evopoint.tidydial.ui.permission.UnusedAppRestrictions
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Each row of the Permissions section shows its state and offers the right button. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PermissionsSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var permissions by mutableStateOf(PermissionsUi())
    private val actions = mutableListOf<PermissionAction>()

    private fun setContent(fontScale: Float = 1f) {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    PermissionsSection(permissions = permissions, onAction = { actions += it })
                }
            }
        }
    }

    private fun clickLabelOf(text: String): String? = composeRule
        .onNode(hasText(text) and hasClickAction())
        .fetchSemanticsNode()
        .config[SemanticsActions.OnClick]
        .label

    @Test
    fun allowedPermissionsOfferNoButton() {
        setContent()
        composeRule.onNodeWithText("Required · Allowed").assertExists()
        composeRule.onNodeWithText("Optional · Allowed").assertExists()
        composeRule.onNodeWithText("Allow").assertDoesNotExist()
        composeRule.onNodeWithText("Open settings").assertDoesNotExist()
        // Unknown or not available: no row about unused apps.
        composeRule.onNodeWithText("Remove permissions if unused").assertDoesNotExist()
    }

    @Test
    fun deniedCallsCanBeAllowed() {
        permissions = PermissionsUi(calls = AccessState.Denied)
        setContent()
        composeRule.onNodeWithText("Optional · Not allowed").assertExists()

        composeRule.onNodeWithText("Allow").performClick()

        assertEquals(listOf(PermissionAction.AllowCalls), actions)
        assertEquals("Allow phone calls", clickLabelOf("Allow"))
    }

    @Test
    fun deniedContactsCanBeAllowed() {
        permissions = PermissionsUi(contacts = AccessState.Denied)
        setContent()
        composeRule.onNodeWithText("Required · Not allowed").assertExists()

        composeRule.onNodeWithText("Allow").performClick()

        assertEquals(listOf(PermissionAction.AllowContacts), actions)
        assertEquals("Allow contacts access", clickLabelOf("Allow"))
    }

    @Test
    fun permanentlyDeniedPermissionsOpenTheAppSettings() {
        permissions = PermissionsUi(contacts = AccessState.PermanentlyDenied, calls = AccessState.PermanentlyDenied)
        setContent()
        composeRule.onNodeWithText("Allow").assertDoesNotExist()
        val buttons = composeRule.onAllNodesWithText("Open settings")

        buttons[0].performClick()
        buttons[1].performClick()

        assertEquals(listOf(PermissionAction.OpenAppSettings, PermissionAction.OpenAppSettings), actions)
        val labels = buttons.fetchSemanticsNodes().map { it.config[SemanticsActions.OnClick].label }
        assertEquals(listOf("Allow contacts access in settings", "Allow phone calls in settings"), labels)
        // Says why, on both rows.
        composeRule.onAllNodesWithText("Android won’t ask again. Allow it in settings.").assertCountEquals(2)
    }

    @Test
    fun theWholeRowIsTheTapTarget() {
        permissions = PermissionsUi(calls = AccessState.Denied, unusedAppRestrictions = UnusedAppRestrictions.On)
        setContent()

        composeRule.onNodeWithText("Phone calls").performClick()
        composeRule.onNodeWithText("Remove permissions if unused").performClick()

        assertEquals(listOf(PermissionAction.AllowCalls, PermissionAction.ManageUnusedAppRestrictions), actions)
        // Rows without anything to do cannot be tapped.
        composeRule.onNode(hasText("Contacts") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun aScreenReaderReadsOutWhenARowChanges() {
        // For example "Allow" turning into "Open settings" when Android no longer shows its dialog.
        permissions = PermissionsUi(calls = AccessState.Denied)
        setContent()

        permissions = PermissionsUi(calls = AccessState.PermanentlyDenied)

        composeRule.onNodeWithText("Phone calls")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(hasText("Android won’t ask again. Allow it in settings."))
    }

    @Test
    fun atLargeFontSizesTheActionMovesUnderTheText() {
        permissions = PermissionsUi(calls = AccessState.Denied, unusedAppRestrictions = UnusedAppRestrictions.On)
        setContent(fontScale = 2f)

        val explanation = composeRule.onNodeWithText("Android may remove permissions", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val turnOff = composeRule.onNodeWithText("Turn off", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(turnOff.top >= explanation.bottom)
        val state = composeRule.onNodeWithText("Optional · Not allowed", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val allow = composeRule.onNodeWithText("Allow", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(allow.top >= state.bottom)
    }

    @Test
    fun atNormalFontSizesTheActionEndsTheRow() {
        permissions = PermissionsUi(unusedAppRestrictions = UnusedAppRestrictions.On)
        setContent()

        val explanation = composeRule.onNodeWithText("Android may remove permissions", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val turnOff = composeRule.onNodeWithText("Turn off", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(turnOff.left >= explanation.right)
    }

    @Test
    fun removalOfUnusedAppPermissionsCanBeTurnedOff() {
        permissions = PermissionsUi(unusedAppRestrictions = UnusedAppRestrictions.On)
        setContent()
        composeRule.onNodeWithText("Remove permissions if unused").assertExists()
        composeRule.onNodeWithText("Android may remove permissions", substring = true).assertExists()

        composeRule.onNodeWithText("Turn off").performClick()

        assertEquals(listOf(PermissionAction.ManageUnusedAppRestrictions), actions)
        assertEquals("Turn off in system settings", clickLabelOf("Turn off"))
    }

    @Test
    fun removalTurnedOffOffersNoButton() {
        permissions = PermissionsUi(unusedAppRestrictions = UnusedAppRestrictions.Off)
        setContent()
        composeRule.onNodeWithText("Remove permissions if unused").assertExists()
        composeRule.onNodeWithText("Turned off").assertExists()
        composeRule.onNodeWithText("Turn off").assertDoesNotExist()
    }
}
