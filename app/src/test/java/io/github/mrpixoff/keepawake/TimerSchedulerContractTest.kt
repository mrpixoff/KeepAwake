package io.github.mrpixoff.keepawake

import io.github.mrpixoff.keepawake.fakes.FakeTimerScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerSchedulerContractTest {

    @Test
    fun start_records_duration_and_marks_scheduler_running() {
        val scheduler = FakeTimerScheduler()

        scheduler.start(3_000L, onTick = {}, onFinish = {})

        assertEquals(3_000L, scheduler.startedDurationMs)
        assertTrue(scheduler.isRunning)
    }

    @Test
    fun advanceBy_emits_ticks_and_finishes_when_remaining_reaches_zero() {
        val scheduler = FakeTimerScheduler()
        val ticks = mutableListOf<Long>()
        var finishes = 0
        scheduler.start(3_000L, onTick = { ticks.add(it) }, onFinish = { finishes++ })

        scheduler.advanceBy(1_000L)
        scheduler.advanceBy(2_000L)

        assertEquals(listOf(2_000L, 0L), ticks)
        assertEquals(1, finishes)
        assertFalse(scheduler.isRunning)
    }

    @Test
    fun cancel_prevents_further_ticks_and_finish() {
        val scheduler = FakeTimerScheduler()
        val ticks = mutableListOf<Long>()
        var finishes = 0
        scheduler.start(3_000L, onTick = { ticks.add(it) }, onFinish = { finishes++ })

        scheduler.cancel()
        scheduler.advanceBy(3_000L)

        assertTrue(ticks.isEmpty())
        assertEquals(0, finishes)
        assertFalse(scheduler.isRunning)
    }

    @Test
    fun start_cancels_previous_run() {
        val scheduler = FakeTimerScheduler()
        val firstTicks = mutableListOf<Long>()
        val secondTicks = mutableListOf<Long>()
        scheduler.start(3_000L, onTick = { firstTicks.add(it) }, onFinish = {})
        scheduler.start(5_000L, onTick = { secondTicks.add(it) }, onFinish = {})

        scheduler.advanceBy(1_000L)

        assertTrue(firstTicks.isEmpty())
        assertEquals(listOf(4_000L), secondTicks)
    }
}
