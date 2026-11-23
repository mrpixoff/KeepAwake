package io.github.mrpixoff.keepawake.fakes

import io.github.mrpixoff.keepawake.core.TimerScheduler

class FakeTimerScheduler : TimerScheduler {
    var startedDurationMs: Long? = null
        private set
    var isRunning: Boolean = false
        private set
    var remainingMs: Long = 0L
        private set

    private var onTick: ((Long) -> Unit)? = null
    private var onFinish: (() -> Unit)? = null

    override fun start(durationMs: Long, onTick: (remainingMs: Long) -> Unit, onFinish: () -> Unit) {
        cancel()
        startedDurationMs = durationMs
        remainingMs = durationMs
        this.onTick = onTick
        this.onFinish = onFinish
        isRunning = true
    }

    override fun cancel() {
        isRunning = false
        onTick = null
        onFinish = null
    }

    fun advanceBy(deltaMs: Long) {
        if (!isRunning) return
        remainingMs = (remainingMs - deltaMs).coerceAtLeast(0L)
        onTick?.invoke(remainingMs)
        if (remainingMs == 0L) {
            isRunning = false
            val callback = onFinish
            onTick = null
            onFinish = null
            callback?.invoke()
        }
    }
}
