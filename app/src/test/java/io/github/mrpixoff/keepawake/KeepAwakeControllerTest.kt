package io.github.mrpixoff.keepawake

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeepAwakeControllerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val timer = FakeTimerScheduler()
    private val clock = FakeClock(nowMs = 1_000L)
    private val writeGuard = FakeWriteSettingsGuard(canWrite = true)
    private val notificationGuard = FakeNotificationGuard(granted = true)
    private lateinit var controller: KeepAwakeController

    @Before
    fun setUp() {
        controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = clock,
            writeSettingsGuard = writeGuard,
            notificationGuard = notificationGuard
        )
    }

    @Test
    fun start_saves_active_state_and_writes_indefinite_timeout() {
        val result = controller.start(Preset.MIN_15)

        assertTrue(result.isSuccess)
        assertTrue(store.state.isActive)
        assertEquals(Preset.MIN_15, store.state.preset)
        assertEquals(6_000, store.state.originalTimeoutMs)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt()), gateway.setCalls)
        assertEquals(store.state, controller.state.value)
        assertEquals(900_000L, timer.startedDurationMs)
    }

    @Test
    fun start_with_indefinite_preset_does_not_start_timer() {
        assertTrue(controller.start(Preset.INDEFINITE).isSuccess)

        assertNull(store.state.deadlineMs)
        assertNull(timer.startedDurationMs)
    }

    @Test
    fun start_fails_when_write_settings_is_not_granted() {
        writeGuard.canWrite = false

        assertTrue(controller.start(Preset.MIN_15).isFailure)
        assertTrue(gateway.setCalls.isEmpty())
        assertFalse(store.state.isActive)
    }

    @Test
    fun start_rolls_back_when_system_write_fails() {
        gateway.failNextSet = true

        assertTrue(controller.start(Preset.MIN_15).isFailure)
        assertFalse(store.state.isActive)
        assertFalse(controller.state.value.isActive)
    }

    @Test
    fun start_again_restores_previous_timeout_before_applying_new_one() {
        controller.start(Preset.MIN_15)

        assertTrue(controller.start(Preset.MIN_30).isSuccess)

        assertEquals(
            listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000, Constants.INDEFINITE_TIMEOUT_MS.toInt()),
            gateway.setCalls
        )
    }

    @Test
    fun stop_restores_original_timeout_and_clears_state() {
        controller.start(Preset.MIN_15)

        assertTrue(controller.stop(StopReason.USER).isSuccess)

        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
        assertFalse(controller.state.value.isActive)
        assertFalse(timer.isRunning)
    }

    @Test
    fun stop_is_idempotent() {
        controller.start(Preset.MIN_15)
        controller.stop(StopReason.USER)

        assertTrue(controller.stop(StopReason.USER).isSuccess)

        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun timer_finish_stops_and_restores_original() {
        controller.start(Preset.MIN_15)

        timer.advanceBy(900_000L)

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun screen_off_and_low_battery_stop_and_restore() {
        controller.start(Preset.MIN_15)
        controller.onScreenOff()
        assertFalse(store.state.isActive)

        controller.start(Preset.MIN_15)
        controller.onBatteryLow(20)
        assertTrue(store.state.isActive)

        controller.onBatteryLow(Constants.LOW_BATTERY_THRESHOLD_PERCENT - 1)
        assertFalse(store.state.isActive)
    }

    @Test
    fun restoreOnProcessStart_restores_timeout_and_drops_state() {
        gateway.value = Constants.INDEFINITE_TIMEOUT_MS.toInt()
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        controller.restoreOnProcessStart()

        assertEquals(listOf(6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
        assertFalse(timer.isRunning)
    }

    @Test
    fun stop_when_restore_fails_keeps_original_for_a_retry() {
        controller.start(Preset.MIN_15)
        gateway.failNextSet = true

        assertTrue(controller.stop(StopReason.USER).isFailure)
        assertTrue(store.state.isActive)
        assertEquals(6_000, store.state.originalTimeoutMs)

        assertTrue(controller.stop(StopReason.USER).isSuccess)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun restoreOnProcessStart_when_restore_fails_keeps_state_for_a_retry() {
        gateway.value = Constants.INDEFINITE_TIMEOUT_MS.toInt()
        gateway.failNextSet = true
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        controller.restoreOnProcessStart()

        assertTrue(store.state.isActive)
        assertTrue(gateway.setCalls.isEmpty())

        controller.restoreOnProcessStart()

        assertFalse(store.state.isActive)
        assertEquals(listOf(6_000), gateway.setCalls)
    }

    @Test
    fun restoreOnProcessStart_is_noop_when_nothing_is_saved() {
        controller.restoreOnProcessStart()

        assertTrue(gateway.setCalls.isEmpty())
        assertFalse(store.state.isActive)
    }

    @Test
    fun remainingMs_tracks_countdown_and_is_null_for_indefinite() {
        controller.start(Preset.MIN_30)
        assertEquals(1_800_000L, controller.remainingMs(clock.nowMs()))

        clock.advanceBy(1_000L)
        assertEquals(1_799_000L, controller.remainingMs(clock.nowMs()))

        controller.start(Preset.INDEFINITE)
        assertNull(controller.remainingMs(clock.nowMs()))
    }
}
