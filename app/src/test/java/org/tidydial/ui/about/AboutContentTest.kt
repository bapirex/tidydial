package org.tidydial.ui.about

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.tidydial.ui.theme.TidyDialTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AboutContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var sourceClicks = 0

    private fun setContent() {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                AboutContent(versionName = "9.8.7", versionCode = 42, onOpenSourceCode = { sourceClicks++ })
            }
        }
    }

    @Test
    fun showsVersion() {
        setContent()
        composeRule.onNodeWithText("Version 9.8.7 (42)").assertExists()
    }

    @Test
    fun sourceCodeRowOpensLink() {
        setContent()
        composeRule.onNodeWithText("Source code").performScrollTo().performClick()
        assertEquals(1, sourceClicks)
    }

    @Test
    fun librariesExpandOnTap() {
        setContent()
        composeRule.onNodeWithText("libphonenumber").assertDoesNotExist()

        composeRule.onNodeWithText("Open-source libraries").performScrollTo().performClick()

        composeRule.onNodeWithText("libphonenumber").assertExists()
    }
}
