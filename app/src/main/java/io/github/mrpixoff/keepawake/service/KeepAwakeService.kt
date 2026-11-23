package io.github.mrpixoff.keepawake.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import io.github.mrpixoff.keepawake.AppContainer
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.receiver.BatteryReceiver
import io.github.mrpixoff.keepawake.receiver.ScreenOffReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class KeepAwakeService : Service() {
    private lateinit var controller: io.github.mrpixoff.keepawake.domain.KeepAwakeController
    private var screenOffReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var observerScope: CoroutineScope? = null
    private var hasBeenActive = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        controller = AppContainer.controller()
        NotificationHelper.createChannel(this)
        startForeground(NOTIFICATION_ID, NotificationHelper.buildNotification(this, controller.state.value))
        syncWakeLock()

        screenOffReceiver = ScreenOffReceiver().also {
            registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        batteryReceiver = BatteryReceiver().also {
            val filter = IntentFilter(Intent.ACTION_BATTERY_LOW)
            filter.addAction(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(it, filter)
        }

        observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                controller.state.collect { state ->
                    if (state.isActive) {
                        hasBeenActive = true
                        acquireWakeLock()
                    } else {
                        releaseWakeLock()
                        if (hasBeenActive) {
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START -> {
                if (!controller.state.value.isActive) {
                    val name = intent.getStringExtra(EXTRA_PRESET) ?: Preset.INDEFINITE.name
                    val preset = runCatching { Preset.valueOf(name) }.getOrDefault(Preset.INDEFINITE)
                    controller.start(preset)
                }
            }
            Constants.ACTION_STOP -> {
                controller.stop(StopReason.USER)
                stopSelf()
            }
            Constants.ACTION_TIMER_EXPIRED -> controller.onTimerFinished()
        }
        // Even if the state collector has not run yet, keep the lock in sync with
        // the authoritative controller state. This is what keeps the screen on
        // on shells (Realme UI / ColorOS) that reset large SCREEN_OFF_TIMEOUT values.
        syncWakeLock()
        startForeground(NOTIFICATION_ID, NotificationHelper.buildNotification(this, controller.state.value))
        // A failed ACTION_START must not leave an idle foreground service behind.
        if (!controller.state.value.isActive && !hasBeenActive) {
            stopSelf()
        }
        return START_RESULT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        screenOffReceiver?.let { unregisterReceiver(it) }
        batteryReceiver?.let { unregisterReceiver(it) }
        screenOffReceiver = null
        batteryReceiver = null
        observerScope?.cancel()
        observerScope = null
        releaseWakeLock()
        if (::controller.isInitialized) {
            controller.stop(StopReason.ERROR)
        }
        super.onDestroy()
    }

    private fun syncWakeLock() {
        if (controller.state.value.isActive) acquireWakeLock() else releaseWakeLock()
    }

    /**
     * Safety net on top of `Settings.System.SCREEN_OFF_TIMEOUT`: some OEM skins
     * (Realme UI / ColorOS / MIUI) ignore unusually large timeout values or
     * silently reset them. A bright screen wake lock keeps the display on
     * regardless of the system timeout, until the mode is stopped.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val lock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            Constants.WAKE_LOCK_TAG
        )
        lock.setReferenceCounted(false)
        runCatching { lock.acquire() }
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wakeLock = null
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_PRESET = "preset"
        val START_RESULT: Int = START_NOT_STICKY

        /**
         * Starts the foreground service. The call can throw
         * `ForegroundServiceStartNotAllowedException` on Android 12+ (for
         * example when the app is in the background). Callers get a failed
         * [Result] instead of a crash and can roll back the controller state.
         */
        fun start(context: Context, preset: Preset): Result<Unit> = runCatching {
            val intent = Intent(context, KeepAwakeService::class.java)
                .setAction(Constants.ACTION_START)
                .putExtra(EXTRA_PRESET, preset.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context): Result<Unit> = runCatching {
            val intent = Intent(context, KeepAwakeService::class.java).setAction(Constants.ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
