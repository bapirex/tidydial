package org.tidydial.ui.contacts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.tidydial.ui.UiState
import org.tidydial.ui.preview.SampleData
import org.tidydial.ui.theme.TidyDialTheme
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FastScrollerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tidyState: UiState
        get() = SampleData.uiState(entries = SampleData.manyTidyEntries, selection = emptySet())

    private lateinit var listState: LazyListState

    private fun setList(state: UiState, dark: Boolean = false) {
        listState = LazyListState()
        composeRule.setContent {
            TidyDialTheme(darkTheme = dark, dynamicColor = false) {
                Surface {
                    ContactList(
                        state = state,
                        onAction = {},
                        contentPadding = PaddingValues(),
                        bottomClearance = 16.dp,
                        listState = listState,
                        modifier = Modifier.testTag("list"),
                    )
                }
            }
        }
    }

    @Test
    fun sectionsFollowListOrderAndBucketSymbolsUnderHash() {
        val sections = ContactIndexer(Locale.ENGLISH).sections(
            listOf("24h Locksmith", "Ada", "alan", "Bob", "Émile", "Zoë"),
        )
        assertEquals(
            listOf(
                IndexSection("#", 0),
                IndexSection("A", 1),
                IndexSection("B", 3),
                IndexSection("E", 4),
                IndexSection("Z", 5),
            ),
            sections,
        )
    }

    @Test
    fun firstItemIndexOffsetsForHeaderItems() {
        val sections = ContactIndexer(Locale.ENGLISH).sections(listOf("Ada", "Bob"), firstItemIndex = 1)
        assertEquals(listOf(IndexSection("A", 1), IndexSection("B", 2)), sections)
    }

    @Test
    fun repeatedLabelKeepsFirstPosition() {
        val sections = ContactIndexer(Locale.ENGLISH).sections(listOf("Ada", "Bob", "Ava"))
        assertEquals(listOf(IndexSection("A", 0), IndexSection("B", 1)), sections)
    }

    @Test
    fun nonLatinScriptsGetTheirOwnLetters() {
        val indexer = ContactIndexer(Locale.forLanguageTag("ru"))
        assertEquals("Ж", indexer.labelOf("Жанна"))
        assertEquals("A", indexer.labelOf("Anna"))
    }

    @Test
    fun railHiddenOnShortLists() {
        setList(SampleData.uiState())
        composeRule.onAllNodesWithContentDescription("Quick scroll index").assertCountEquals(0)
    }

    @Test
    fun jumpActionScrollsToSection() {
        setList(tidyState)
        composeRule.onNodeWithContentDescription("Jump to S")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val first = tidyState.groups[listState.firstVisibleItemIndex - 1].displayName
        assertTrue("first visible is $first", first.startsWith("S"))
    }

    @Test
    fun numberIsNeverCutOffNextToRailAndBadges() {
        setList(tidyState)
        composeRule.onNodeWithContentDescription("Jump to G").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // The row merges its text into one TalkBack sentence, so this is checked visually:
        // Gus Golf's number must show in full, with its Google and Google Meet badges wrapped.
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/contacts_fast_scroll_badges.png")
    }

    @Test
    fun railScreenshot() {
        setList(tidyState)
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/contacts_fast_scroll.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun draggingShowsBubble() {
        setList(tidyState, dark = true)
        composeRule.onNodeWithContentDescription("Quick scroll index").performTouchInput {
            down(center)
            moveTo(center.copy(y = height * 0.62f))
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/contacts_fast_scroll_drag.png")
        composeRule.onNodeWithContentDescription("Quick scroll index").performTouchInput { up() }
        assertTrue(listState.firstVisibleItemIndex > 0)
    }
}
