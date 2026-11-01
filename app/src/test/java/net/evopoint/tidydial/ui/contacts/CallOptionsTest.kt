package net.evopoint.tidydial.ui.contacts

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import net.evopoint.tidydial.model.AppChat
import net.evopoint.tidydial.model.CallOption
import net.evopoint.tidydial.ui.CallRequest
import net.evopoint.tidydial.ui.UiAction
import net.evopoint.tidydial.ui.UiEvent
import net.evopoint.tidydial.ui.UiState
import net.evopoint.tidydial.ui.permission.PermissionRefusals
import net.evopoint.tidydial.ui.preview.SampleData
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CallOptionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<UiAction>()
    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** A call whose activity needs CALL_PHONE, with the app's chat to open instead; made up. */
    private val guardedCall = CallOption.App(
        label = "Signal",
        isVideo = false,
        packageName = "org.example.signal",
        activityName = "org.example.signal.CallActivity",
        dataId = 9002,
        mimeType = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call",
        needsCallPermission = true,
        chat = AppChat(
            packageName = "org.example.signal",
            activityName = "org.example.signal.ChatActivity",
            dataId = 9003,
            mimeType = "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact",
        ),
    )

    /** Records permission requests instead of showing the system dialog; answered by [answer]. */
    private val registry = object : ActivityResultRegistry() {
        val requests = mutableListOf<Pair<Int, Any?>>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            requests += requestCode to input
        }
    }

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    private fun setScreen(
        state: UiState,
        events: Flow<UiEvent> = emptyFlow(),
        setContent: (@Composable () -> Unit) -> Unit = composeRule::setContent,
    ) {
        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                TidyDialTheme(dynamicColor = false) {
                    ContactsScreen(
                        state = state,
                        events = events,
                        onAction = { actions += it },
                        exampleNumber = { _, _ -> null },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The user's answer to the one permission dialog shown. */
    private fun answer(granted: Boolean) {
        val requestCode = registry.requests.single().first
        composeRule.runOnIdle { registry.dispatchResult(requestCode, granted) }
        composeRule.waitForIdle()
    }

    private fun nextStartedActivity(): Intent? = shadowOf(app).nextStartedActivity

    @Test
    fun onlyThePhoneAppDialsWithoutAsking() {
        setScreen(SampleData.uiState().copy(call = CallRequest.Launch(CallOption.Phone("3105550142,12"))))

        val intent = nextStartedActivity()!!
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("3105550142,12", intent.data!!.schemeSpecificPart)
        assertEquals(listOf(UiAction.DismissCall), actions)
        composeRule.onNodeWithText("Opens your phone app").assertDoesNotExist()
    }

    @Test
    fun noAppToCallWithIsReported() {
        // Starting an activity nothing handles now throws, like on a device without a dialer.
        shadowOf(app).checkActivities(true)
        setScreen(SampleData.uiState().copy(call = CallRequest.Launch(CallOption.Phone("2125550123"))))

        assertEquals(listOf(UiAction.CallFailed), actions)
    }

    @Test
    fun otherAppsAreOfferedInASheet() {
        setScreen(SampleData.uiState().copy(call = SampleData.callChoice))

        composeRule.onNodeWithText("Call Dana Testperson").assertExists()
        composeRule.onNodeWithText("+1 646-555-0144").assertExists()
        composeRule.onNodeWithText("Threema").assertExists()
        composeRule.onNodeWithText("Video call").assertExists()
        assertEquals(null, nextStartedActivity())

        composeRule.onNodeWithText("Phone").performClick()
        composeRule.waitForIdle()

        // The ViewModel decides how to start it (the pick may need the call permission first).
        assertEquals(listOf(UiAction.PickCall(CallOption.Phone("+1 646-555-0144"))), actions)
        assertEquals(null, nextStartedActivity())
    }

    @Test
    fun aSecondTapWhileTheSheetClosesStartsNoSecondCall() {
        setScreen(SampleData.uiState().copy(call = SampleData.callChoice))

        // Both picks reach the options while the sheet is closing (a touch on the sheet would stop
        // the animation instead, as TalkBack's double tap does not).
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Phone").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Threema").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(listOf(UiAction.PickCall(CallOption.Phone("+1 646-555-0144"))), actions)
    }

    @Test
    fun anAppCallStartsTheCheckedActivity() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        setScreen(SampleData.uiState().copy(call = CallRequest.Launch(guardedCall)))

        val intent = nextStartedActivity()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("org.example.signal.CallActivity", intent.component!!.className)
        assertEquals("content://com.android.contacts/data/9002", intent.data.toString())
        assertEquals(listOf(UiAction.DismissCall), actions)
    }

    @Test
    fun aCallThatNeedsThePermissionAsksForIt() {
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        assertEquals(listOf<Any?>(Manifest.permission.CALL_PHONE), registry.requests.map { it.second })
        assertEquals(emptyList<UiAction>(), actions)
        assertNull(nextStartedActivity())

        answer(granted = true)

        assertEquals(listOf(UiAction.CallPermissionResult(granted = true, canAskAgain = true)), actions)
    }

    @Test
    fun theDialogIsNotShownAgainWhenTheActivityIsRecreated() {
        val restoration = StateRestorationTester(composeRule)
        setScreen(
            SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)),
            setContent = restoration::setContent,
        )

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(1, registry.requests.size)
        answer(granted = true)
        assertEquals(listOf(UiAction.CallPermissionResult(granted = true, canAskAgain = true)), actions)
    }

    @Test
    fun aRefusalThatCanBeAskedAgainIsReported() {
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE, true)
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        answer(granted = false)

        assertEquals(listOf(UiAction.CallPermissionResult(granted = false, canAskAgain = true)), actions)
    }

    @Test
    fun aPermanentRefusalIsReported() {
        // Refused before, and no rationale expected now: the system did not show the dialog.
        PermissionRefusals(app).markRefused(Manifest.permission.CALL_PHONE)
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        answer(granted = false)

        assertEquals(listOf(UiAction.CallPermissionResult(granted = false, canAskAgain = false)), actions)
    }

    @Test
    fun aSecondRefusalIsPermanent() {
        // Refused once: a rationale is expected until the user refuses again.
        rationale(true)
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        rationale(false)
        answer(granted = false)

        assertEquals(listOf(UiAction.CallPermissionResult(granted = false, canAskAgain = false)), actions)
    }

    @Test
    fun aFirstDialogClosedWithoutAnAnswerIsNotPermanent() {
        // Back or a tap outside on Android 11+: refused, but no rationale expected, as for a
        // permanent refusal. Never refused here before, so the dialog can still be shown.
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        answer(granted = false)

        assertEquals(listOf(UiAction.CallPermissionResult(granted = false, canAskAgain = true)), actions)
        assertTrue(PermissionRefusals(app).wasRefused(Manifest.permission.CALL_PHONE))
    }

    @Test
    fun aGrantForgetsEarlierRefusals() {
        PermissionRefusals(app).markRefused(Manifest.permission.CALL_PHONE)
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        answer(granted = true)

        assertFalse(PermissionRefusals(app).wasRefused(Manifest.permission.CALL_PHONE))
    }

    private fun rationale(expected: Boolean) =
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE, expected)

    @Test
    fun aGrantedPermissionIsNotAskedForAgain() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        setScreen(SampleData.uiState().copy(call = CallRequest.RequestPermission(guardedCall)))

        assertEquals(emptyList<Pair<Int, Any?>>(), registry.requests)
        assertEquals(listOf(UiAction.CallPermissionResult(granted = true, canAskAgain = true)), actions)
    }

    @Test
    fun aRefusedCallOpensTheChat() {
        setScreen(SampleData.uiState().copy(call = CallRequest.OpenChat(guardedCall.chat!!)))

        val intent = nextStartedActivity()!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("org.example.signal.ChatActivity", intent.component!!.className)
        assertEquals("content://com.android.contacts/data/9003", intent.data.toString())
        assertEquals("vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact", intent.type)
        assertEquals(listOf(UiAction.DismissCall), actions)
    }

    @Test
    fun aChatThatCannotOpenIsReported() {
        shadowOf(app).checkActivities(true)
        setScreen(SampleData.uiState().copy(call = CallRequest.OpenChat(guardedCall.chat!!)))

        assertEquals(listOf(UiAction.CallFailed), actions)
    }

    @Test
    fun aPermanentRefusalOffersTheSettings() {
        setScreen(
            SampleData.uiState(),
            events = flowOf(UiEvent.CallPermissionDenied("Signal", permanently = true)),
        )

        composeRule.onNodeWithText("Calling through Signal needs permission to make phone calls").assertExists()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(UiAction.OpenAppSettings), actions)
    }
}
