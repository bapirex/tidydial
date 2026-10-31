package net.evopoint.tidydial.ui.permission

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.concurrent.futures.ResolvableFuture
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.evopoint.tidydial.ui.options.PermissionsSection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.evopoint.tidydial.ui.theme.TidyDialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Permissions section asks again, opens the app's settings once the system dialog no longer
 * appears, opens the system page about unused apps, and rechecks on resume and after answers.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PermissionSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private var restrictions: UnusedAppRestrictions? = null
    private var restrictionChecks = 0
    private val restrictionsSource = UnusedAppRestrictionsSource {
        restrictionChecks++
        restrictions
    }

    /** Records launches instead of showing system screens; answered by [answer]. */
    private val registry = object : ActivityResultRegistry() {
        val requests = mutableListOf<Pair<Int, Any?>>()
        var noActivityForIntents = false

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            if (input is Intent && noActivityForIntents) throw ActivityNotFoundException()
            requests += requestCode to if (input is Array<*>) input.toList() else input
        }
    }

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = registry
    }

    /** Stands in for the activity's lifecycle, so the test can leave and come back. */
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    @Before
    fun resume() {
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
    }

    private fun showSection() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalLifecycleOwner provides lifecycleOwner,
            ) {
                TidyDialTheme(dynamicColor = false) {
                    val settings = rememberPermissionSettings(restrictionsSource)
                    PermissionsSection(permissions = settings.ui, onAction = settings.onAction)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performClick()
        composeRule.waitForIdle()
    }

    private fun answer(result: Any) {
        val requestCode = registry.requests.last().first
        composeRule.runOnIdle { registry.dispatchResult(requestCode, result) }
        composeRule.waitForIdle()
    }

    /** Leaves the app (for example for the system settings) and comes back. */
    private fun leaveAndReturn(whileAway: () -> Unit) {
        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        whileAway()
        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
    }

    private fun grantContacts() =
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    @Test
    fun allowAsksForTheCallPermissionAgain() {
        grantContacts()
        showSection()
        tap("Allow")
        assertEquals(listOf(Manifest.permission.CALL_PHONE), registry.requests.single().second)

        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        answer(mapOf(Manifest.permission.CALL_PHONE to true))

        composeRule.onNodeWithText("Optional · Allowed").assertExists()
        composeRule.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test
    fun allowAsksForContactsAccessAgain() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")

        assertEquals(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            registry.requests.single().second,
        )
    }

    @Test
    fun refusedWhileARationaleIsExpectedOffersAllowAgain() {
        grantContacts()
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE, true)
        showSection()
        tap("Allow")

        answer(mapOf(Manifest.permission.CALL_PHONE to false))

        composeRule.onNodeWithText("Optional · Not allowed").assertExists()
        composeRule.onNodeWithText("Allow").assertExists()
    }

    @Test
    fun permanentlyRefusedOpensTheAppSettings() {
        // Refused before, and without a rationale expected now: no dialog was shown.
        grantContacts()
        PermissionRefusals(app).markRefused(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")
        answer(mapOf(Manifest.permission.CALL_PHONE to false))
        composeRule.onNodeWithText("Allow").assertDoesNotExist()
        // Says why "Allow" turned into "Open settings", read out by screen readers too.
        composeRule.onNodeWithText("Android won’t ask again. Allow it in settings.").assertExists()

        tap("Open settings")

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${app.packageName}", started.dataString)
        assertEquals(1, registry.requests.size)
    }

    @Test
    fun aSecondCallRefusalOpensTheAppSettings() {
        grantContacts()
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE, true)
        showSection()
        tap("Allow")

        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE, false)
        answer(mapOf(Manifest.permission.CALL_PHONE to false))

        composeRule.onNodeWithText("Open settings").assertExists()
    }

    @Test
    fun aCallDialogClosedWithoutAnAnswerStillOffersAllow() {
        // Back or a tap outside on Android 11+ looks like a permanent refusal, but the next
        // request still shows the dialog.
        grantContacts()
        showSection()
        tap("Allow")

        answer(mapOf(Manifest.permission.CALL_PHONE to false))

        composeRule.onNodeWithText("Allow").assertExists()
        composeRule.onNodeWithText("Android won’t ask again. Allow it in settings.").assertDoesNotExist()
    }

    @Test
    fun noSettingsAppToOpenDoesNotCrash() {
        // Some locked-down devices disable the settings app.
        shadowOf(app).checkActivities(true)
        grantContacts()
        PermissionRefusals(app).markRefused(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")
        answer(mapOf(Manifest.permission.CALL_PHONE to false))

        tap("Open settings")

        composeRule.onNodeWithText("Open settings").assertExists()
    }

    @Test
    fun permanentlyRefusedContactsOpenTheAppSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")
        answer(mapOf(Manifest.permission.READ_CONTACTS to false, Manifest.permission.WRITE_CONTACTS to false))

        composeRule.onNodeWithText("Required · Not allowed").assertExists()
        tap("Open settings")

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun aCancelledContactsRequestStillOffersAllow() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")

        answer(emptyMap<String, Boolean>())

        composeRule.onNodeWithText("Allow").assertExists()
        composeRule.onNodeWithText("Open settings").assertDoesNotExist()
    }

    @Test
    fun grantedInTheSettingsShowsOnReturn() {
        grantContacts()
        PermissionRefusals(app).markRefused(Manifest.permission.CALL_PHONE)
        showSection()
        tap("Allow")
        answer(mapOf(Manifest.permission.CALL_PHONE to false))
        tap("Open settings")

        leaveAndReturn { shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE) }

        composeRule.onNodeWithText("Optional · Allowed").assertExists()
        composeRule.onNodeWithText("Open settings").assertDoesNotExist()
        assertFalse(PermissionRefusals(app).wasRefused(Manifest.permission.CALL_PHONE))
    }

    @Test
    fun turnedOffByAndroidMeanwhileOffersAllowOnReturn() {
        // For example after Android removed the permissions of an app unused for months.
        grantContacts()
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        showSection()
        composeRule.onNodeWithText("Optional · Allowed").assertExists()

        leaveAndReturn { shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE) }

        composeRule.onNodeWithText("Optional · Not allowed").assertExists()
        composeRule.onNodeWithText("Allow").assertExists()
    }

    @Test
    fun theUnusedAppsRowShowsOnlyWhereItApplies() {
        grantContacts()
        showSection()
        composeRule.onNodeWithText("Remove permissions if unused").assertDoesNotExist()

        restrictions = UnusedAppRestrictions.On
        leaveAndReturn {}

        composeRule.onNodeWithText("Remove permissions if unused").assertExists()
        composeRule.onNodeWithText("Turn off").assertExists()
    }

    @Test
    fun turnOffOpensTheSystemPageAndRechecksAfter() {
        grantContacts()
        restrictions = UnusedAppRestrictions.On
        showSection()
        tap("Turn off")
        val intent = registry.requests.single().second as Intent
        // Android 11+: the app's page in the system settings (Robolectric runs a recent SDK).
        assertEquals("package:${app.packageName}", intent.dataString)

        restrictions = UnusedAppRestrictions.Off
        val checks = restrictionChecks
        answer(ActivityResult(Activity.RESULT_CANCELED, null))

        composeRule.onNodeWithText("Turned off").assertExists()
        composeRule.onNodeWithText("Turn off").assertDoesNotExist()
        assertTrue(restrictionChecks > checks)
    }

    @Test
    fun turnOffFallsBackToTheAppSettingsWithoutTheSystemPage() {
        grantContacts()
        restrictions = UnusedAppRestrictions.On
        registry.noActivityForIntents = true
        showSection()

        tap("Turn off")

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)
        assertNull(registry.requests.firstOrNull())
    }

    @Test
    fun systemStatusesMapToTheRow() {
        assertEquals(UnusedAppRestrictions.Off, unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.DISABLED))
        // On Android 8 to 10 through Google Play, and on Android 11 and 12+.
        assertEquals(UnusedAppRestrictions.On, unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.API_30_BACKPORT))
        assertEquals(UnusedAppRestrictions.On, unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.API_30))
        assertEquals(UnusedAppRestrictions.On, unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.API_31))
        assertNull(unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.ERROR))
        assertNull(unusedAppRestrictionsOf(UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE))
    }

    @Test
    fun theSystemStatusIsReadFromAndroid() = runTest {
        // Robolectric runs Android 12+, where androidx.core answers right away.
        assertEquals(UnusedAppRestrictions.On, SystemUnusedAppRestrictions(app).status())

        shadowOf(app.packageManager).setAutoRevokeWhitelisted(true)

        assertEquals(UnusedAppRestrictions.Off, SystemUnusedAppRestrictions(app).status())
    }

    @Test
    fun awaitWaitsForALaterResult() = runTest {
        val future = ResolvableFuture.create<Int>()
        val result = async(start = CoroutineStart.UNDISPATCHED) { future.await() }
        assertFalse(result.isCompleted)

        future.set(UnusedAppRestrictionsConstants.API_31)

        assertEquals(UnusedAppRestrictionsConstants.API_31, result.await())
    }

    @Test
    fun awaitRethrowsTheCauseOfAFailure() = runTest {
        val future = ResolvableFuture.create<Int>()
        val failure = IllegalStateException("no verifier")
        val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { future.await() } }

        future.setException(failure)

        // Not wrapped in an ExecutionException (coroutines may hand over a copy, for its stack trace).
        val thrown = result.await().exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(failure.message, thrown?.message)
    }

    @Test
    fun awaitCancelsTheFutureWhenCancelled() = runTest {
        val future = ResolvableFuture.create<Int>()
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { future.await() }

        waiting.cancelAndJoin()

        assertTrue(future.isCancelled)
    }
}
