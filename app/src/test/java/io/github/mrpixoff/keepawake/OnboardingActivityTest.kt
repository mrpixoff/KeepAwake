package io.github.mrpixoff.keepawake

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.Button
import io.github.mrpixoff.keepawake.ui.MainActivity
import io.github.mrpixoff.keepawake.ui.OnboardingActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnboardingActivityTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Shadows.shadowOf(context as android.app.Application).setSystemService(
            android.content.Context.AUDIO_SERVICE,
            io.mockk.mockk<android.media.AudioManager>(relaxed = true)
        )
        AppContainer.setOnboardingCompleted(false)
        AppContainer.setWriteSettingsAllowedForTests(false)
    }

    @After
    fun tearDown() {
        AppContainer.setOnboardingCompleted(false)
        AppContainer.setWriteSettingsAllowedForTests(null)
    }

    @Test
    fun first_launcher_start_redirects_to_onboarding() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val activity = Robolectric.buildActivity(MainActivity::class.java, launcherIntent).setup().get()

        val next = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(next)
        assertEquals(OnboardingActivity::class.java.name, next.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun continue_button_saves_flag_and_finishes() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.continueButton).performClick()

        assertTrue(AppContainer.isOnboardingCompleted())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun continue_button_starts_main_activity_on_first_run() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.continueButton).performClick()

        val next = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(next)
        assertEquals(MainActivity::class.java.name, next.component?.className)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun continue_from_help_just_finishes() {
        val intent = Intent().putExtra(OnboardingActivity.EXTRA_FROM_HELP, true)
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java, intent).setup().get()

        activity.findViewById<Button>(R.id.continueButton).performClick()

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun write_settings_button_opens_system_settings() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.onboardingWriteSettingsButton).performClick()

        assertEquals(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Shadows.shadowOf(activity).nextStartedActivity.action
        )
    }

    @Test
    fun notification_button_requests_post_notifications_permission() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.onboardingNotificationsButton).performClick()

        val request = Shadows.shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in request.requestedPermissions)
    }

    @Test
    fun granted_write_settings_hides_button_and_shows_check() {
        AppContainer.setWriteSettingsAllowedForTests(true)

        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.onboardingWriteCheck).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.onboardingWriteSettingsButton).visibility)
    }
}
