package io.github.mrpixoff.keepawake

import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelContractTest {

    @Test
    fun presets_have_expected_durations() {
        assertNull(Preset.INDEFINITE.durationMs)
        assertEquals(900_000L, Preset.MIN_15.durationMs)
        assertEquals(1_800_000L, Preset.MIN_30.durationMs)
        assertEquals(3_600_000L, Preset.HOUR_1.durationMs)
        assertEquals(4, Preset.entries.size)
    }

    @Test
    fun constants_are_stable() {
        assertEquals(Int.MAX_VALUE.toLong(), Constants.INDEFINITE_TIMEOUT_MS)
        assertEquals(30_000L, Constants.TILE_REFRESH_INTERVAL_MS)
        assertEquals(15, Constants.LOW_BATTERY_THRESHOLD_PERCENT)
        assertEquals("keepawake_fgs", Constants.NOTIFICATION_CHANNEL_ID)
        assertTrue(Constants.PREF_ORIGINAL_TIMEOUT_MS.isNotBlank())
        assertEquals("io.github.mrpixoff.keepawake.action.STOP", Constants.ACTION_STOP)
        assertEquals(7, StopReason.entries.size)
    }

    @Test
    fun inactive_state_has_no_active_data() {
        val state = KeepAwakeState.inactive()

        assertEquals(false, state.isActive)
        assertNull(state.startedAtMs)
        assertNull(state.deadlineMs)
        assertNull(state.originalTimeoutMs)
    }

    @Test
    fun active_state_keeps_original_timeout_and_deadline() {
        val state = KeepAwakeState.active(
            preset = Preset.MIN_15,
            startedAtMs = 1_000L,
            originalTimeoutMs = 6_000
        )

        assertEquals(true, state.isActive)
        assertEquals(Preset.MIN_15, state.preset)
        assertEquals(1_000L, state.startedAtMs)
        assertEquals(901_000L, state.deadlineMs)
        assertEquals(6_000, state.originalTimeoutMs)
    }

    @Test
    fun active_indefinite_state_has_no_deadline() {
        val state = KeepAwakeState.active(
            preset = Preset.INDEFINITE,
            startedAtMs = 1_000L,
            originalTimeoutMs = 6_000
        )

        assertNull(state.deadlineMs)
    }
}
