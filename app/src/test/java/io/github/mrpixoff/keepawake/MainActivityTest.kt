package io.github.mrpixoff.keepawake

import android.Manifest
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.domain.KeepAwakeController
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager
import io.github.mrpixoff.keepawake.fakes.FakeAppStateStore
import io.github.mrpixoff.keepawake.fakes.FakeClock
import io.github.mrpixoff.keepawake.fakes.FakeNotificationGuard
import io.github.mrpixoff.keepawake.fakes.FakeSystemSettingsGateway
import io.github.mrpixoff.keepawake.fakes.FakeTimerScheduler
import io.github.mrpixoff.keepawake.fakes.FakeWriteSettingsGuard
import io.github.mrpixoff.keepawake.service.KeepAwakeService
import io.github.mrpixoff.keepawake.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class MainActivityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()

    @Before
    fun setUp() {
        Shadows.shadowOf(context as android.app.Application).setSystemService(
            android.content.Context.AUDIO_SERVICE,
            io.mockk.mockk<android.media.AudioManager>(relaxed = true)
        )
        val controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = FakeTimerScheduler(),
            clock = FakeClock(nowMs = 1_000L),
            writeSettingsGuard = FakeWriteSettingsGuard(canWrite = true),
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
        AppContainer.setSelectedPreset(Preset.INDEFINITE)
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
        AppContainer.setWriteSettingsAllowedForTests(null)
    }

    @Test
    fun toggle_button_starts_mode_and_service() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.toggleButton).performClick()

        assertTrue(store.state.isActive)
        val started = Shadows.shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(Constants.ACTION_START, started.action)
        assertEquals(Preset.INDEFINITE.name, started.getStringExtra(KeepAwakeService.EXTRA_PRESET))
    }

    @Test
    fun toggle_button_stops_an_active_mode() {
        AppContainer.controller().start(Preset.MIN_15)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.toggleButton).performClick()

        assertFalse(store.state.isActive)
        assertEquals(Constants.ACTION_STOP, Shadows.shadowOf(context).nextStartedService.action)
    }

    @Test
    fun preset_selection_is_persisted_for_the_tile() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<RadioButton>(R.id.radio30).isChecked = true

        assertEquals(Preset.MIN_30, AppContainer.selectedPreset())
    }

    @Test
    fun write_settings_button_opens_system_settings() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.buttonWriteSettings).performClick()

        assertEquals(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Shadows.shadowOf(activity).nextStartedActivity.action
        )
    }

    @Test
    fun notification_button_requests_post_notifications_permission() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<Button>(R.id.buttonNotifications).performClick()

        val request = Shadows.shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in request.requestedPermissions)
    }

    @Test
    fun selected_preset_is_restored_when_activity_reopens() {
        AppContainer.setSelectedPreset(Preset.MIN_30)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertTrue(activity.findViewById<android.widget.RadioButton>(R.id.radio30).isChecked)
    }

    @Test
    fun active_preset_is_highlighted_even_if_saved_selection_differs() {
        AppContainer.setSelectedPreset(Preset.INDEFINITE)
        AppContainer.controller().start(Preset.MIN_30)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertTrue(activity.findViewById<RadioButton>(R.id.radio30).isChecked)
        assertFalse(activity.findViewById<RadioButton>(R.id.radioIndefinite).isChecked)
    }

    @Test
    fun timer_keeps_digits_large_and_moves_caption_out_of_the_number() {
        AppContainer.controller().start(Preset.MIN_15)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(
            "15:00",
            activity.findViewById<TextView>(R.id.timerText).text.toString()
        )
        assertEquals(
            android.view.View.VISIBLE,
            activity.findViewById<android.view.View>(R.id.timerLabel).visibility
        )
    }

    @Test
    fun permissions_card_is_hidden_when_both_permissions_are_granted() {
        AppContainer.setWriteSettingsAllowedForTests(true)
        Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(
            android.view.View.GONE,
            activity.findViewById<android.view.View>(R.id.permissionsCard).visibility
        )
    }

    @Test
    fun granted_write_settings_hides_button_and_shows_check() {
        AppContainer.setWriteSettingsAllowedForTests(true)

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(android.view.View.VISIBLE, activity.findViewById<android.view.View>(R.id.writeSettingsCheck).visibility)
        assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.buttonWriteSettings).visibility)
    }

    @Test
    fun toggle_button_uses_stop_style_when_active() {
        AppContainer.controller().start(Preset.MIN_15)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val button = activity.findViewById<Button>(R.id.toggleButton)

        assertEquals(context.getString(R.string.toggle_off), button.text.toString())
        assertEquals(
            androidx.core.content.ContextCompat.getColor(context, R.color.on_stop),
            button.currentTextColor
        )
    }

    @Test
    fun toggle_button_uses_accent_style_when_idle() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        val button = activity.findViewById<Button>(R.id.toggleButton)

        assertEquals(context.getString(R.string.toggle_on), button.text.toString())
        assertEquals(
            androidx.core.content.ContextCompat.getColor(context, R.color.on_accent),
            button.currentTextColor
        )
    }

    @Test
    fun status_text_reflects_active_state() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        AppContainer.controller().start(Preset.MIN_15)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(
            context.getString(R.string.status_active),
            activity.findViewById<TextView>(R.id.statusText).text.toString()
        )
    }
}
