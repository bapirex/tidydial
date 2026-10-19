package org.tidydial.ui.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.tidydial.R
import org.tidydial.ui.PermissionStatus
import org.tidydial.ui.theme.TidyDialTheme

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
        composeRule.onNodeWithText(string(R.string.permission_allow)).performScrollTo().performClick()
        assertEquals(1, requests)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun permanentlyDenied_buttonOpensSettings() {
        show(PermissionStatus.PermanentlyDenied)
        composeRule.onNodeWithText(string(R.string.permission_permanently_denied)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_allow)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.permission_open_settings))
            .performScrollTo()
            .performClick()
        assertEquals(0, requests)
        assertEquals(1, settingsOpened)
    }
}
