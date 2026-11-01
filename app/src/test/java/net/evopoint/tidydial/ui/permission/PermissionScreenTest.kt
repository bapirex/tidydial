package net.evopoint.tidydial.ui.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.PermissionStatus
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PermissionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var requests = 0
    private var settingsOpened = 0

    private fun show(status: PermissionStatus) {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                PermissionScreen(
                    status = status,
                    onRequest = { requests++ },
                    onOpenSettings = { settingsOpened++ },
                )
            }
        }
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun denied_buttonRequestsPermission() {
        show(PermissionStatus.Denied)
        composeRule.onNodeWithText(string(R.string.permission_denied_hint)).assertIsDisplayed()
        // Pinned below the scrolling content, so it is on screen without scrolling.
        composeRule.onNodeWithText(string(R.string.permission_continue)).assertIsDisplayed().performClick()
        assertEquals(1, requests)
        assertEquals(0, settingsOpened)
    }

    @Test
    @Config(qualifiers = "w360dp-h400dp-xxhdpi")
    fun shortWindow_actionsScrollIntoReach() {
        RuntimeEnvironment.setFontScale(1.5f)
        show(PermissionStatus.PermanentlyDenied)
        // Too short to pin the actions: they scroll with the content and can be reached.
        composeRule.onNodeWithText(string(R.string.permission_open_settings)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.permission_footnote)).performScrollTo().assertIsDisplayed()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun eachPermissionIsExplainedAndLabelled() {
        show(PermissionStatus.Denied)
        composeRule.onNodeWithText(string(R.string.permission_contacts_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_required)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_call_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_optional)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_call_body)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun permanentlyDenied_buttonOpensSettings() {
        show(PermissionStatus.PermanentlyDenied)
        composeRule.onNodeWithText(string(R.string.permission_permanently_denied)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_continue)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.permission_open_settings))
            .assertIsDisplayed()
            .performClick()
        assertEquals(0, requests)
        assertEquals(1, settingsOpened)
    }
}
