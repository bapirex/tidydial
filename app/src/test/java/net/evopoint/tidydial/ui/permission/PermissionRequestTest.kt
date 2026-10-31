package net.evopoint.tidydial.ui.permission

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.R
import net.evopoint.tidydial.ui.PermissionStatus
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The explainer's button asks for contacts access and the optional call permission at once. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PermissionRequestTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val results = mutableListOf<UiAction.PermissionResult>()

    private val contactsAndCalls = listOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
    )
    private val contactsOnly = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    /** Records permission requests instead of showing the system dialogs; answered by [answer]. */
    private val registry = object : ActivityResultRegistry() {
        val requests = mutableListOf<Pair<Int, List<String>>>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            requests += requestCode to (input as Array<*>).map { it as String }
        }
    }

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    private fun showScreen(setContent: (@Composable () -> Unit) -> Unit = composeRule::setContent) {
        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                TidyDialTheme(dynamicColor = false) {
                    PermissionScreen(
                        status = PermissionStatus.Denied,
                        onRequest = rememberPermissionRequest { results += it },
                        onOpenSettings = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tapContinue() {
        composeRule.onNodeWithText(app.getString(R.string.permission_continue)).performClick()
        composeRule.waitForIdle()
    }

    /** The user's answers to the dialogs of the last request. */
    private fun answer(vararg answers: Pair<String, Boolean>) {
        val requestCode = registry.requests.last().first
        composeRule.runOnIdle { registry.dispatchResult(requestCode, mapOf(*answers)) }
        composeRule.waitForIdle()
    }

    @Test
    fun theButtonAsksForContactsAndCallsTogether() {
        showScreen()
        tapContinue()

        assertEquals(listOf(contactsAndCalls), registry.requests.map { it.second })
        assertEquals(emptyList<UiAction>(), results)
    }

    @Test
    fun bothGrantedContinues() {
        showScreen()
        tapContinue()

        answer(
            Manifest.permission.READ_CONTACTS to true,
            Manifest.permission.WRITE_CONTACTS to true,
            Manifest.permission.CALL_PHONE to true,
        )

        assertEquals(listOf(UiAction.PermissionResult(granted = true, canAskAgain = false)), results)
    }

    @Test
    fun contactsWithoutCallsContinuesAsWell() {
        showScreen()
        tapContinue()

        answer(
            Manifest.permission.READ_CONTACTS to true,
            Manifest.permission.WRITE_CONTACTS to true,
            Manifest.permission.CALL_PHONE to false,
        )

        assertEquals(listOf(UiAction.PermissionResult(granted = true, canAskAgain = false)), results)
        assertEquals(1, registry.requests.size)
    }

    @Test
    fun refusedContactsCanBeAskedAgain() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        showScreen()
        tapContinue()

        answer(
            Manifest.permission.READ_CONTACTS to false,
            Manifest.permission.WRITE_CONTACTS to false,
            Manifest.permission.CALL_PHONE to true,
        )

        // A granted call permission does not stand in for contacts access.
        assertEquals(listOf(UiAction.PermissionResult(granted = false, canAskAgain = true)), results)
    }

    @Test
    fun permanentlyRefusedContactsAreReported() {
        // No rationale expected after a denial: the system will not show the dialog again.
        showScreen()
        tapContinue()

        answer(
            Manifest.permission.READ_CONTACTS to false,
            Manifest.permission.WRITE_CONTACTS to false,
            Manifest.permission.CALL_PHONE to false,
        )

        assertEquals(listOf(UiAction.PermissionResult(granted = false, canAskAgain = false)), results)
    }

    @Test
    fun aGrantedCallPermissionIsNotAskedForAgain() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        showScreen()
        tapContinue()

        assertEquals(listOf(contactsOnly), registry.requests.map { it.second })
    }

    @Test
    fun aRefusedCallPermissionIsNotAskedForAgainWithContacts() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        showScreen()
        tapContinue()
        answer(
            Manifest.permission.READ_CONTACTS to false,
            Manifest.permission.WRITE_CONTACTS to false,
            Manifest.permission.CALL_PHONE to false,
        )

        tapContinue()

        assertEquals(listOf(contactsAndCalls, contactsOnly), registry.requests.map { it.second })
    }

    @Test
    fun aRefusedCallPermissionIsRememberedWhenTheActivityIsRecreated() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        val restoration = StateRestorationTester(composeRule)
        showScreen(setContent = restoration::setContent)
        tapContinue()
        answer(
            Manifest.permission.READ_CONTACTS to false,
            Manifest.permission.WRITE_CONTACTS to false,
            Manifest.permission.CALL_PHONE to false,
        )

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        tapContinue()

        assertEquals(contactsOnly, registry.requests.last().second)
    }

    @Test
    fun anAnswerArrivingAfterTheActivityIsRecreatedIsReported() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS, true)
        val restoration = StateRestorationTester(composeRule)
        showScreen(setContent = restoration::setContent)
        tapContinue()

        // Rotated while the system dialog is open: the answer reaches the recreated screen.
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        answer(
            Manifest.permission.READ_CONTACTS to false,
            Manifest.permission.WRITE_CONTACTS to false,
            Manifest.permission.CALL_PHONE to false,
        )

        assertEquals(listOf(UiAction.PermissionResult(granted = false, canAskAgain = true)), results)
        // The refused call permission was recorded too, so asking again is for contacts only.
        tapContinue()
        assertEquals(listOf(contactsAndCalls, contactsOnly), registry.requests.map { it.second })
    }

    @Test
    fun contactsGrantedBeforeCountEvenWhenMissingFromTheAnswer() {
        // Contacts were granted in the meantime (for example in settings); the dialog was dismissed.
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        showScreen()
        tapContinue()

        answer()

        assertEquals(listOf(UiAction.PermissionResult(granted = true, canAskAgain = false)), results)
    }
}
