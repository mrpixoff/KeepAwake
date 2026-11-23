package io.github.mrpixoff.keepawake

import android.content.Intent
import android.os.BatteryManager
import android.service.quicksettings.Tile
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.domain.KeepAwakeController
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager
import io.github.mrpixoff.keepawake.fakes.FakeAppStateStore
import io.github.mrpixoff.keepawake.fakes.FakeClock
import io.github.mrpixoff.keepawake.fakes.FakeNotificationGuard
import io.github.mrpixoff.keepawake.fakes.FakeSystemSettingsGateway
import io.github.mrpixoff.keepawake.fakes.FakeTimerScheduler
import io.github.mrpixoff.keepawake.fakes.FakeWriteSettingsGuard
import io.github.mrpixoff.keepawake.receiver.BatteryReceiver
import io.github.mrpixoff.keepawake.receiver.BootRestoreReceiver
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
@Config(sdk = [24, 33])
class IntegrationFlowTest {

    private val context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val timer = FakeTimerScheduler()
    private val clock = FakeClock(nowMs = 1_000L)
    private val writeGuard = FakeWriteSettingsGuard(canWrite = true)
    private val notificationGuard = FakeNotificationGuard(granted = true)
    private lateinit var controller: KeepAwakeController

    @Before
    fun setUp() {
        AppContainer.setClockForTests(clock)
        AppContainer.setWriteSettingsAllowedForTests(true)
        controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = clock,
            writeSettingsGuard = writeGuard,
            notificationGuard = notificationGuard
        )
        AppContainer.setControllerForTests(controller)
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
        AppContainer.setWriteSettingsAllowedForTests(null)
        AppContainer.setClockForTests(null)
    }

    @Test
    fun tile_click_starts_service_and_notification_stop_restores_timeout() {
        AppContainer.setSelectedPreset(Preset.MIN_15)
        val tile = Robolectric.buildService(KeepAwakeTileService::class.java).create().get()

        tile.onClick()

        val startIntent = Shadows.shadowOf(context).nextStartedService
        assertNotNull(startIntent)
        assertEquals(Constants.ACTION_START, startIntent.action)
        val service = Robolectric.buildService(KeepAwakeService::class.java).create().get()
        service.onStartCommand(startIntent, 0, 1)
        assertTrue(store.state.isActive)

        service.onStartCommand(
            Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_STOP),
            0,
            2
        )

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun reboot_restores_original_timeout() {
        gateway.value = Constants.INDEFINITE_TIMEOUT_MS.toInt()
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        BootRestoreReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(listOf(6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
    }

    @Test
    fun service_kill_then_cold_start_restores_original_timeout() {
        gateway.value = Constants.INDEFINITE_TIMEOUT_MS.toInt()
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        AppContainer.controller().restoreOnProcessStart()

        assertEquals(listOf(6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
    }

    @Test
    fun low_battery_below_threshold_stops_and_restores() {
        assertTrue(controller.start(Preset.MIN_15).isSuccess)

        BatteryReceiver().onReceive(
            context,
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 14)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
        )

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun revoked_write_settings_stops_mode_and_marks_tile_unavailable() {
        assertTrue(controller.start(Preset.MIN_15).isSuccess)
        AppContainer.setWriteSettingsAllowedForTests(false)
        val tile = Robolectric.buildService(KeepAwakeTileService::class.java).create().get()

        tile.onStartListening()

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
        assertEquals(Tile.STATE_UNAVAILABLE, tile.qsTile.state)
    }

    @Test
    fun screen_off_and_timer_together_restore_only_once() {
        assertTrue(controller.start(Preset.MIN_15).isSuccess)

        controller.onScreenOff()
        timer.advanceBy(900_000L)
        controller.stop(StopReason.USER)

        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
    }

    @Test
    fun notifications_permission_denied_does_not_prevent_foreground_service() {
        val deniedGuard = FakeNotificationGuard(granted = false)
        val deniedController = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = clock,
            writeSettingsGuard = writeGuard,
            notificationGuard = deniedGuard
        )
        AppContainer.setControllerForTests(deniedController)
        val service = Robolectric.buildService(KeepAwakeService::class.java).create().get()

        service.onStartCommand(
            Intent(context, KeepAwakeService::class.java)
                .setAction(Constants.ACTION_START)
                .putExtra(KeepAwakeService.EXTRA_PRESET, Preset.MIN_15.name),
            0,
            1
        )

        assertFalse(deniedController.canShowNotification())
        assertTrue(store.state.isActive)
        assertNotNull(Shadows.shadowOf(service).lastForegroundNotification)
    }
}
