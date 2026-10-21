package net.evopoint.tidydial.ui.contacts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    /** Runs the rail's TalkBack "Jump to [label]" action. */
    private fun jumpTo(label: String) {
        val rail = composeRule.onNodeWithContentDescription("Quick scroll index")
        val action = rail.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Jump to $label" }
        composeRule.runOnIdle { action.action() }
        composeRule.waitForIdle()
    }

    private fun firstVisibleName(): String =
        tidyState.groups[listState.firstVisibleItemIndex - 1].displayName

    @Test
    fun jumpActionScrollsToSection() {
        setList(tidyState)
        jumpTo("S")
        assertTrue("first visible is ${firstVisibleName()}", firstVisibleName().startsWith("S"))
    }

    @Test
    fun railIsOneAccessibleControl() {
        setList(tidyState)
        // Letters are drawn only; no tiny, overlapping focus targets.
        composeRule.onAllNodesWithContentDescription("Jump to", substring = true).assertCountEquals(0)
        val config = composeRule.onNodeWithContentDescription("Quick scroll index").fetchSemanticsNode().config
        assertEquals("Section #", config[SemanticsProperties.StateDescription])
        val sections = ContactIndexer(Locale.ENGLISH).sections(tidyState.groups.map { it.displayName })
        assertEquals((sections.size - 1).toFloat(), config[SemanticsProperties.ProgressBarRangeInfo].range.endInclusive)
        assertEquals(sections.size, config[SemanticsActions.CustomActions].size)
    }

    @Test
    fun setProgressMovesBySection() {
        setList(tidyState)
        val sections = ContactIndexer(Locale.ENGLISH).sections(tidyState.groups.map { it.displayName })
        val target = sections.indexOfFirst { it.label == "M" }
        composeRule.onNodeWithContentDescription("Quick scroll index")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(target.toFloat()) }
        composeRule.waitForIdle()
        assertTrue("first visible is ${firstVisibleName()}", firstVisibleName().startsWith("M"))
        composeRule.onNodeWithContentDescription("Quick scroll index")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Section M"))
    }

    @Test
    fun numberIsNeverCutOffNextToRailAndBadges() {
        setList(tidyState)
        jumpTo("G")
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
