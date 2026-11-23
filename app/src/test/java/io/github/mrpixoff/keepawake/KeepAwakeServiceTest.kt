package io.github.mrpixoff.keepawake

import android.content.Context
import android.content.Intent
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
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeepAwakeServiceTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val timer = FakeTimerScheduler()
    private lateinit var service: KeepAwakeService

    @Before
    fun setUp() {
        ShadowPowerManager.clearWakeLocks()
        val controller = KeepAwakeController(
            context = context,
            store = store,
            timeoutManager = ScreenTimeoutManager(gateway, store),
            timer = timer,
            clock = FakeClock(nowMs = 1_000L),
            writeSettingsGuard = FakeWriteSettingsGuard(canWrite = true),
            notificationGuard = FakeNotificationGuard(granted = true)
        )
        AppContainer.setControllerForTests(controller)
        service = Robolectric.buildService(KeepAwakeService::class.java).create().get()
    }

    @After
    fun tearDown() {
        AppContainer.setControllerForTests(null)
        ShadowPowerManager.clearWakeLocks()
    }

    @Test
    fun start_action_activates_mode_and_shows_foreground_notification() {
        val intent = Intent(context, KeepAwakeService::class.java)
            .setAction(Constants.ACTION_START)
            .putExtra(KeepAwakeService.EXTRA_PRESET, Preset.MIN_15.name)

        val result = service.onStartCommand(intent, 0, 1)

        assertEquals(KeepAwakeService.START_RESULT, result)
        assertTrue(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt()), gateway.setCalls)
        assertNotNull(Shadows.shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun stop_action_restores_timeout_and_stops_mode() {
        startMode()

        service.onStartCommand(Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_STOP), 0, 2)

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun timer_expired_action_stops_mode() {
        startMode()

        service.onStartCommand(Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_TIMER_EXPIRED), 0, 3)

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun screen_off_broadcast_restores_timeout() {
        startMode()

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun battery_low_broadcast_restores_timeout() {
        startMode()

        context.sendBroadcast(Intent(Intent.ACTION_BATTERY_LOW))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun auto_stop_from_screen_off_shuts_down_the_foreground_service() {
        startMode()

        AppContainer.controller().onScreenOff()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(store.state.isActive)
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun onDestroy_restores_timeout_when_mode_is_still_active() {
        startMode()

        service.onDestroy()

        assertFalse(store.state.isActive)
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt(), 6_000), gateway.setCalls)
    }

    @Test
    fun start_acquires_a_screen_bright_wake_lock() {
        startMode()

        val lock = ShadowPowerManager.getLatestWakeLock()
        assertNotNull("A wake lock must guard the screen on OEM skins", lock)
        assertTrue("Wake lock must be held while the mode is active", lock.isHeld)
        assertEquals(
            Constants.WAKE_LOCK_TAG,
            org.robolectric.shadow.api.Shadow.extract<ShadowPowerManager.ShadowWakeLock>(lock).tag
        )
    }
    @Test
    fun stop_releases_the_wake_lock() {
        startMode()
        val lock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(lock.isHeld)

        service.onStartCommand(Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_STOP), 0, 2)

        assertFalse("Wake lock must be released when the mode stops", lock.isHeld)
    }

    @Test
    fun timer_expiry_releases_the_wake_lock() {
        startMode()
        val lock = ShadowPowerManager.getLatestWakeLock()

        service.onStartCommand(Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_TIMER_EXPIRED), 0, 3)

        assertFalse(lock.isHeld)
    }

    @Test
    fun onDestroy_releases_the_wake_lock() {
        startMode()
        val lock = ShadowPowerManager.getLatestWakeLock()

        service.onDestroy()

        assertFalse(lock.isHeld)
    }

    private fun startMode() {
        val intent = Intent(context, KeepAwakeService::class.java)
            .setAction(Constants.ACTION_START)
            .putExtra(KeepAwakeService.EXTRA_PRESET, Preset.MIN_15.name)
        service.onStartCommand(intent, 0, 1)
    }
}
