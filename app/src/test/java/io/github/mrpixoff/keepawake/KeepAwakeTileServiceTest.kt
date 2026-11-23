package io.github.mrpixoff.keepawake

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
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
import io.github.mrpixoff.keepawake.ui.KeepAwakeTileService
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
class KeepAwakeTileServiceTest {

    private val context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val timer = FakeTimerScheduler()
    private val writeGuard = FakeWriteSettingsGuard(canWrite = true)
    private lateinit var service: KeepAwakeTileService
    private val clock = FakeClock(nowMs = 1_000L)

    @Before
    fun setUp() {
        AppContainer.setClockForTests(clock)
        val controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = clock,
            writeSettingsGuard = writeGuard,
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
        AppContainer.setWriteSettingsAllowedForTests(true)
        service = Robolectric.buildService(KeepAwakeTileService::class.java).create().get()
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
        AppContainer.setWriteSettingsAllowedForTests(null)
        AppContainer.setClockForTests(null)
    }

    @Test
    fun onStartListening_maps_active_state_to_tile() {
        AppContainer.controller().start(Preset.MIN_15)

        service.onStartListening()

        val tile = service.qsTile
        assertEquals(context.getString(R.string.tile_label_active), tile.label.toString())
        assertTrue(tile.subtitle.toString().contains("15:00"))
        assertEquals(Tile.STATE_ACTIVE, tile.state)
        assertNotNull(tile.icon)
    }

    @Test
    fun onStartListening_maps_inactive_state_to_tile() {
        service.onStartListening()

        val tile = service.qsTile
        assertEquals(context.getString(R.string.tile_label_inactive), tile.label.toString())
        assertEquals(Tile.STATE_INACTIVE, tile.state)
    }

    @Test
    fun onClick_starts_mode_and_service() {
        AppContainer.setSelectedPreset(Preset.MIN_30)

        service.onClick()

        assertTrue(store.state.isActive)
        assertEquals(Preset.MIN_30, store.state.preset)
        val started = Shadows.shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(Constants.ACTION_START, started.action)
        assertEquals(Preset.MIN_30.name, started.getStringExtra(KeepAwakeService.EXTRA_PRESET))
    }

    @Test
    fun onClick_stops_mode_and_service() {
        AppContainer.controller().start(Preset.MIN_15)

        service.onClick()

        assertFalse(store.state.isActive)
        val started = Shadows.shadowOf(context).nextStartedService
        assertEquals(Constants.ACTION_STOP, started.action)
    }

    @Test
    fun onClick_without_write_permission_opens_settings_and_does_not_start() {
        AppContainer.setWriteSettingsAllowedForTests(false)

        service.onClick()

        assertFalse(store.state.isActive)
        val activity = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(activity)
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, activity.action)
    }

    @Test
    fun tile_shows_error_state_when_write_permission_is_missing() {
        AppContainer.controller().start(Preset.MIN_15)
        AppContainer.setWriteSettingsAllowedForTests(false)

        service.onStartListening()

        assertEquals(Tile.STATE_UNAVAILABLE, service.qsTile.state)
        assertEquals(context.getString(R.string.tile_label_error), service.qsTile.label.toString())
    }
}
