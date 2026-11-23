package io.github.mrpixoff.keepawake

import android.app.PendingIntent
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

/**
 * Regression tests for the Android 14+ (API 34/35) Quick Settings tile crash:
 * `startActivityAndCollapse(Intent)` is deprecated there and reading tile state
 * from a click could throw in SystemUI. The tile must instead use an immutable
 * PendingIntent and never let a failed foreground-service start escape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeepAwakeTileServiceApi35Test {

    private val context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val clock = FakeClock(nowMs = 1_000L)
    private lateinit var tileService: TestTileService

    @Before
    fun setUp() {
        val controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = FakeTimerScheduler(),
            clock = clock,
            writeSettingsGuard = FakeWriteSettingsGuard(canWrite = true),
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
        AppContainer.setWriteSettingsAllowedForTests(true)
        AppContainer.setClockForTests(clock)
        AppContainer.setSelectedPreset(Preset.MIN_15)
        tileService = Robolectric.buildService(TestTileService::class.java).create().get()
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
        AppContainer.setWriteSettingsAllowedForTests(null)
        AppContainer.setClockForTests(null)
    }

    @Test
    fun missing_permission_collapses_an_immutable_pending_intent_on_api34() {
        AppContainer.setWriteSettingsAllowedForTests(false)
        tileService.settingsResolvable = true

        tileService.onClick()

        val pendingIntent = tileService.collapsedPendingIntent
        assertNotNull("API 34+ must collapse via PendingIntent", pendingIntent)
        assertTrue(
            "PendingIntent must be immutable (Android 12+ requirement)",
            Shadows.shadowOf(pendingIntent).isImmutable
        )
        val saved = requireNotNull(Shadows.shadowOf(pendingIntent).savedIntent)
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, saved.action)
        assertFalse(store.state.isActive)
    }

    @Test
    fun missing_settings_activity_falls_back_to_main_activity() {
        AppContainer.setWriteSettingsAllowedForTests(false)
        tileService.settingsResolvable = false

        tileService.onClick()

        val pendingIntent = tileService.collapsedPendingIntent
        assertNotNull(pendingIntent)
        val saved = requireNotNull(Shadows.shadowOf(pendingIntent).savedIntent)
        assertEquals(MainActivity::class.java.name, saved.component?.className)
        assertFalse(store.state.isActive)
    }

    @Test
    fun click_starts_mode_and_marks_the_tile_active() {
        tileService.onClick()

        assertTrue(store.state.isActive)
        assertEquals(Preset.MIN_15, store.state.preset)
        assertEquals(Tile.STATE_ACTIVE, tileService.qsTile.state)
    }

    @Test
    fun click_rolls_back_mode_when_foreground_service_start_is_blocked() {
        tileService.startResult = Result.failure(
            IllegalStateException("ForegroundServiceStartNotAllowedException")
        )

        tileService.onClick()

        assertFalse("A blocked FGS start must not leave a phantom mode", store.state.isActive)
        assertEquals(
            listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000),
            gateway.setCalls
        )
        assertEquals(Tile.STATE_INACTIVE, tileService.qsTile.state)
    }

    @Test
    fun click_while_active_stops_the_mode_and_tile() {
        AppContainer.controller().start(Preset.MIN_15)

        tileService.onClick()

        assertFalse(store.state.isActive)
        val started = Shadows.shadowOf(context).nextStartedService
        assertEquals(Constants.ACTION_STOP, started.action)
        assertEquals(Tile.STATE_INACTIVE, tileService.qsTile.state)
    }

    @Test
    fun tile_reports_error_state_when_permission_was_revoked() {
        AppContainer.controller().start(Preset.MIN_15)
        AppContainer.setWriteSettingsAllowedForTests(false)

        tileService.onStartListening()

        assertEquals(Tile.STATE_UNAVAILABLE, tileService.qsTile.state)
        assertEquals(context.getString(R.string.tile_label_error), tileService.qsTile.label.toString())
    }

    /** Test double with overridable seams around final framework methods. */
    class TestTileService : KeepAwakeTileService() {
        var collapsedPendingIntent: PendingIntent? = null
        var collapsedIntent: Intent? = null
        var settingsResolvable = true
        var startResult: Result<Unit> = Result.success(Unit)

        override fun startModeService(preset: Preset): Result<Unit> = startResult

        override fun canResolve(intent: Intent): Boolean = settingsResolvable

        override fun collapseToPendingIntent(pendingIntent: PendingIntent) {
            collapsedPendingIntent = pendingIntent
        }

        override fun collapseToIntent(intent: Intent) {
            collapsedIntent = intent
        }
    }
}
