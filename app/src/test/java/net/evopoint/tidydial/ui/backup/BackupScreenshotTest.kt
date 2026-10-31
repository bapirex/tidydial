package net.evopoint.tidydial.ui.backup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.emptyFlow
import net.evopoint.tidydial.data.BackupKind
import net.evopoint.tidydial.ui.BackupDialog
import net.evopoint.tidydial.ui.BackupError
import net.evopoint.tidydial.ui.ProgressKind
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.UpdateProgress
import net.evopoint.tidydial.ui.contacts.ContactsScreen
import net.evopoint.tidydial.ui.contacts.UpdateProgressContent
import net.evopoint.tidydial.ui.options.SheetFrame
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val DIR = "src/test/screenshots"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BackupScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<UiAction>()

    // region Prompt

    @Test
    fun prompt() = capturePrompt("backup_prompt")

    @Test
    @Config(qualifiers = "+night")
    fun promptDark() = capturePrompt("backup_prompt_dark", darkTheme = true)

    @Test
    fun promptFontScale() = capturePrompt("backup_prompt_font_1_5", fontScale = 1.5f)

    /** The folder could not be used: said in the prompt, above its buttons. */
    @Test
    fun promptError() = capturePrompt("backup_prompt_error", error = BackupError.FOLDER_FAILED)

    private fun capturePrompt(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float = 1f,
        error: BackupError? = null,
    ) {
        composeRule.setContent {
            SheetFrame(darkTheme = darkTheme, fontScale = fontScale) {
                BackupPromptContent(onChooseFolder = {}, onContinueWithout = {}, error = error)
            }
        }
        composeRule.onRoot().captureRoboImage(filePath = "$DIR/$name.png")
    }

    /** The prompt hosted by the list: its buttons report the choice. */
    @Test
    fun promptContinuesWithoutBackup() {
        setScreen(SampleData.uiState().copy(backupDialog = BackupDialog.FirstUpdate))
        composeRule.onNodeWithText("Continue without").performClick()
        assertEquals(listOf<UiAction>(UiAction.ContinueWithoutBackup), actions)
    }

    // endregion

    // region Dialogs

    @Test
    fun restoreConfirm() = captureDialog("restore_confirm", restoreState())

    @Test
    @Config(qualifiers = "+night")
    fun restoreConfirmDark() = captureDialog("restore_confirm_dark", restoreState(), dark = true)

    @Test
    @Config(fontScale = 1.5f)
    fun restoreConfirmFontScale() = captureDialog("restore_confirm_font_1_5", restoreState())

    @Test
    fun restoreConfirmSnapshot() = captureDialog(
        "restore_confirm_snapshot",
        SampleData.uiState().copy(
            restore = SampleData.restoreConfirmation.copy(
                kind = BackupKind.SNAPSHOT,
                count = 1,
                changedSince = 0,
                notFound = 0,
            ),
        ),
    )

    @Test
    fun backupFailed() = captureDialog(
        "backup_failed",
        SampleData.uiState().copy(backupDialog = BackupDialog.WriteFailed(SampleData.backupUi.folderName)),
    )

    @Test
    fun restoreConfirmReportsTheAnswer() {
        setScreen(restoreState())
        composeRule.onNodeWithText("Restore 23 numbers from", substring = true).assertExists()
        composeRule.onNodeWithText("2 numbers were changed since and will be kept.").assertExists()
        composeRule.onNodeWithText("1 number couldn’t be found.").assertExists()
        composeRule.onNodeWithText("Restore").performClick()
        assertEquals(listOf<UiAction>(UiAction.ConfirmRestore), actions)
    }

    @Test
    fun backupFailedCanUpdateWithoutBackup() {
        setScreen(SampleData.uiState().copy(backupDialog = BackupDialog.WriteFailed("TidyDial backups")))
        composeRule.onNodeWithText("Update without backup").performClick()
        assertEquals(listOf<UiAction>(UiAction.UpdateWithoutBackup), actions)
    }

    @Test
    fun backupFailedShowsWhyTheFolderCouldNotBeUsed() {
        setScreen(
            SampleData.uiState().copy(
                backupDialog = BackupDialog.WriteFailed("TidyDial backups"),
                backup = SampleData.backupUi.copy(error = BackupError.FOLDER_FAILED),
            ),
        )
        composeRule.onNodeWithText("Couldn’t use this folder. Try another one.").assertExists()
    }

    @Test
    fun progressSaysItIsBackingUpAndKeepsTheInstruction() {
        composeRule.setContent {
            TidyDialTheme(dynamicColor = false) {
                UpdateProgressContent(UpdateProgress(0, 48, ProgressKind.APPLY, isBackingUp = true))
            }
        }
        composeRule.onNodeWithText("Saving a backup first…")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        composeRule.onNodeWithText("Keep TidyDial open until this finishes.").assertExists()
    }

    private fun restoreState() = SampleData.uiState().copy(restore = SampleData.restoreConfirmation)

    /** The dialog over the list, once the entrance animations are done. */
    private fun captureDialog(name: String, state: UiState, dark: Boolean = false) {
        setScreen(state, dark)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        captureScreenRoboImage(filePath = "$DIR/$name.png")
    }

    // endregion

    private fun setScreen(state: UiState, dark: Boolean = false) {
        composeRule.setContent {
            TidyDialTheme(darkTheme = dark, dynamicColor = false) {
                ContactsScreen(
                    state = state,
                    events = emptyFlow(),
                    onAction = { actions += it },
                    exampleNumber = { _, _ -> null },
                )
            }
        }
    }
}
