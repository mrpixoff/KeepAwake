package io.github.mrpixoff.keepawake

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
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
import io.github.mrpixoff.keepawake.receiver.ScreenOffReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReceiversTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = Constants.INDEFINITE_TIMEOUT_MS.toInt())
    private val store = FakeAppStateStore()
    private lateinit var controller: KeepAwakeController

    @Before
    fun setUp() {
        controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = FakeTimerScheduler(),
            clock = FakeClock(nowMs = 2_000L),
            writeSettingsGuard = FakeWriteSettingsGuard(canWrite = true),
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
    }

    @Test
    fun bootRestoreReceiver_restores_timeout_after_reboot() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        BootRestoreReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(listOf(6_000), gateway.setCalls)
        assertFalse(store.state.isActive)
    }

    @Test
    fun bootRestoreReceiver_handles_package_replaced_too() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        BootRestoreReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertEquals(listOf(6_000), gateway.setCalls)
    }

    @Test
    fun bootRestoreReceiver_does_nothing_without_saved_state() {
        BootRestoreReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(gateway.setCalls.isEmpty())
    }

    @Test
    fun screenOffReceiver_stops_mode_and_restores_timeout() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        ScreenOffReceiver().onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))

        assertFalse(store.state.isActive)
        assertEquals(listOf(6_000), gateway.setCalls)
    }

    @Test
    fun batteryReceiver_stops_when_level_is_below_threshold() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 14)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)

        BatteryReceiver().onReceive(context, intent)

        assertFalse(store.state.isActive)
        assertEquals(listOf(6_000), gateway.setCalls)
    }

    @Test
    fun batteryReceiver_keeps_mode_when_level_is_above_threshold() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 20)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)

        BatteryReceiver().onReceive(context, intent)

        assertTrue(store.state.isActive)
        assertTrue(gateway.setCalls.isEmpty())
    }

    @Test
    fun batteryReceiver_handles_battery_low_action_without_extras() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        BatteryReceiver().onReceive(context, Intent(Intent.ACTION_BATTERY_LOW))

        assertFalse(store.state.isActive)
        assertEquals(listOf(6_000), gateway.setCalls)
    }
}
