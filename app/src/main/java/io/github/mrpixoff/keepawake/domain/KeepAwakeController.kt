package io.github.mrpixoff.keepawake.domain

import android.content.Context
import io.github.mrpixoff.keepawake.core.Clock
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import io.github.mrpixoff.keepawake.core.TimerScheduler
import io.github.mrpixoff.keepawake.data.AppStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth shared by Activity, Service and TileService.
 * State is mirrored to [AppStateStore] so it survives process death.
 */
class KeepAwakeController(
    private val context: Context,
    private val store: AppStateStore,
    private val timeoutManager: ScreenTimeoutManager,
    private val timer: TimerScheduler,
    private val clock: Clock,
    private val writeSettingsGuard: WriteSettingsGuard,
    private val notificationGuard: NotificationGuard
) {
    private val mutableState = MutableStateFlow(store.load())
    val state: StateFlow<KeepAwakeState> = mutableState.asStateFlow()

    fun start(preset: Preset): Result<Unit> {
        if (!writeSettingsGuard.canWrite(context)) {
            return Result.failure(SecurityException("WRITE_SETTINGS is not granted"))
        }
        if (store.load().isActive) {
            stop(StopReason.USER)
        }
        val original = timeoutManager.captureOriginalIfNeeded()
            ?: return Result.failure(IllegalStateException("No system timeout to restore"))

        val newState = KeepAwakeState.active(
            preset = preset,
            startedAtMs = clock.nowMs(),
            originalTimeoutMs = original
        )
        store.save(newState)
        if (!timeoutManager.applyActiveTimeout()) {
            store.clear()
            mutableState.value = store.load()
            return Result.failure(IllegalStateException("Failed to write SCREEN_OFF_TIMEOUT"))
        }

        mutableState.value = newState
        scheduleTimer(newState)
        return Result.success(Unit)
    }

    fun stop(reason: StopReason): Result<Unit> {
        if (!store.load().isActive) return Result.success(Unit)

        timer.cancel()
        if (!timeoutManager.restoreOriginal()) {
            // Keep the saved original: losing it would make every later restore impossible.
            mutableState.value = store.load()
            return Result.failure(IllegalStateException("Failed to restore SCREEN_OFF_TIMEOUT"))
        }
        store.clear()
        mutableState.value = store.load()
        return Result.success(Unit)
    }

    fun onTimerFinished() {
        stop(StopReason.TIMER)
    }

    fun onScreenOff() {
        stop(StopReason.SCREEN_OFF)
    }

    fun onBatteryLow(levelPercent: Int) {
        if (levelPercent < Constants.LOW_BATTERY_THRESHOLD_PERCENT) {
            stop(StopReason.LOW_BATTERY)
        }
    }

    /**
     * Called on cold start / reboot. We always restore the original timeout
     * and drop the active state rather than trying to restart a foreground
     * service from the background.
     */
    fun restoreOnProcessStart() {
        if (!store.load().isActive) return
        timer.cancel()
        if (!timeoutManager.restoreOriginal()) return
        store.clear()
        mutableState.value = store.load()
    }

    fun remainingMs(nowMs: Long = clock.nowMs()): Long? {
        val deadline = mutableState.value.deadlineMs ?: return null
        return (deadline - nowMs).coerceAtLeast(0L)
    }

    fun canShowNotification(): Boolean = notificationGuard.isGranted(context)

    private fun scheduleTimer(state: KeepAwakeState) {
        val deadline = state.deadlineMs ?: return
        timer.start(deadline - state.startedAtMs!!, onTick = {}, onFinish = { onTimerFinished() })
    }
}
