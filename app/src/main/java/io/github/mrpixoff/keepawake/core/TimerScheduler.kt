package io.github.mrpixoff.keepawake.core

import android.os.CountDownTimer

interface TimerScheduler {
    fun start(durationMs: Long, onTick: (remainingMs: Long) -> Unit, onFinish: () -> Unit)
    fun cancel()
}

class CountDownTimerScheduler : TimerScheduler {
    private var timer: CountDownTimer? = null

    override fun start(durationMs: Long, onTick: (remainingMs: Long) -> Unit, onFinish: () -> Unit) {
        cancel()
        val newTimer = object : CountDownTimer(durationMs, Constants.TIMER_TICK_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                onTick(millisUntilFinished)
            }

            override fun onFinish() {
                timer = null
                onFinish()
            }
        }
        timer = newTimer
        newTimer.start()
    }

    override fun cancel() {
        timer?.cancel()
        timer = null
    }
}
