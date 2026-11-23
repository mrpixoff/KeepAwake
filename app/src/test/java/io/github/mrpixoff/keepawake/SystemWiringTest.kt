package io.github.mrpixoff.keepawake

import android.app.AppOpsManager
import android.os.Looper
import android.os.Process
import android.provider.Settings
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.CountDownTimerScheduler
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.core.SystemClockImpl
import io.github.mrpixoff.keepawake.data.AndroidSystemSettingsGateway
import io.github.mrpixoff.keepawake.data.PrefsAppStateStore
import io.github.mrpixoff.keepawake.domain.AndroidNotificationGuard
import io.github.mrpixoff.keepawake.domain.AndroidWriteSettingsGuard
import io.github.mrpixoff.keepawake.domain.KeepAwakeController
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * End-to-end wiring check with the *real* Android implementations:
 * WRITE_SETTINGS app-op -> Settings.System.SCREEN_OFF_TIMEOUT -> persistence ->
 * CountDownTimer -> restore. Only the device hardware is replaced by Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemWiringTest {

    private val context = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    private val gateway = AndroidSystemSettingsGateway(context)
    private val store = PrefsAppStateStore(context)

    @Before
    fun setUp() {
        store.clear()
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, ORIGINAL_TIMEOUT_MS)
        allowWriteSettings(true)
    }

    @After
    fun tearDown() {
        store.clear()
        allowWriteSettings(true)
        Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, ORIGINAL_TIMEOUT_MS)
    }

    @Test
    fun toggle_start_really_raises_system_screen_timeout_and_timer_restores_it() {
        val controller = controller()

        assertTrue("WRITE_SETTINGS must be effectively granted", controller.start(Preset.MIN_15).isSuccess)

        assertTrue(controller.state.value.isActive)
        assertEquals(
            "Active mode must pin SCREEN_OFF_TIMEOUT to 'never'",
            Constants.INDEFINITE_TIMEOUT_MS.toInt(),
            gateway.getTimeoutMs()
        )
        assertEquals(ORIGINAL_TIMEOUT_MS, store.load().originalTimeoutMs)

        // Let the real CountDownTimer run to completion in virtual time.
        Shadows.shadowOf(Looper.getMainLooper())
            .idleFor(Duration.ofMinutes(15).plusSeconds(2))

        assertFalse("Timer expiry must switch the mode off", controller.state.value.isActive)
        assertEquals(
            "Timer expiry must restore the user's original timeout",
            ORIGINAL_TIMEOUT_MS,
            gateway.getTimeoutMs()
        )
        assertFalse(store.load().isActive)
    }

    @Test
    fun manual_stop_restores_the_original_timeout() {
        val controller = controller()
        assertTrue(controller.start(Preset.INDEFINITE).isSuccess)
        assertEquals(Constants.INDEFINITE_TIMEOUT_MS.toInt(), gateway.getTimeoutMs())

        assertTrue(controller.stop(StopReason.USER).isSuccess)

        assertFalse(controller.state.value.isActive)
        assertEquals(ORIGINAL_TIMEOUT_MS, gateway.getTimeoutMs())
    }

    @Test
    fun start_without_write_settings_permission_does_not_touch_the_timeout() {
        allowWriteSettings(false)
        val controller = controller()

        val result = controller.start(Preset.MIN_15)

        assertTrue(result.isFailure)
        assertFalse(controller.state.value.isActive)
        assertEquals(
            "Without permission the system setting must stay untouched",
            ORIGINAL_TIMEOUT_MS,
            gateway.getTimeoutMs()
        )
    }

    private fun controller() = KeepAwakeController(
        context = context,
        store = store,
        timeoutManager = ScreenTimeoutManager(gateway, store),
        timer = CountDownTimerScheduler(),
        clock = SystemClockImpl(),
        writeSettingsGuard = AndroidWriteSettingsGuard(),
        notificationGuard = AndroidNotificationGuard()
    )

    private fun allowWriteSettings(allowed: Boolean) {
        val mode = if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED
        Shadows.shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(
                AppOpsManager.OPSTR_WRITE_SETTINGS,
                Process.myUid(),
                context.packageName,
                mode
            )
    }

    private companion object {
        const val ORIGINAL_TIMEOUT_MS = 6_000
    }
}
