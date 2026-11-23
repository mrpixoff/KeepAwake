package io.github.mrpixoff.keepawake

import io.github.mrpixoff.keepawake.R
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.domain.TileStateMapper
import io.github.mrpixoff.keepawake.domain.TileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileStateMapperTest {

    @Test
    fun inactive_state_maps_to_inactive_tile() {
        val view = TileStateMapper.map(KeepAwakeState.inactive(), nowMs = 1_000L, canWrite = true)

        assertEquals(R.string.tile_label_inactive, view.labelRes)
        assertEquals(R.string.tile_subtitle_off, view.subtitleRes)
        assertNull(view.subtitleArg)
        assertEquals(R.drawable.ic_tile_inactive, view.iconRes)
        assertEquals(TileUiState.INACTIVE, view.uiState)
    }

    @Test
    fun active_indefinite_state_maps_to_active_tile_without_timer() {
        val state = KeepAwakeState.active(Preset.INDEFINITE, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 5_000L, canWrite = true)

        assertEquals(R.string.tile_label_active, view.labelRes)
        assertEquals(R.string.tile_subtitle_indefinite, view.subtitleRes)
        assertNull(view.subtitleArg)
        assertEquals(TileUiState.ACTIVE, view.uiState)
    }

    @Test
    fun active_timed_state_formats_remaining_minutes_and_seconds() {
        val state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 1_000L, canWrite = true)

        assertEquals(R.string.tile_subtitle_remaining, view.subtitleRes)
        assertEquals("15:00", view.subtitleArg)
    }

    @Test
    fun remaining_time_uses_ceil_seconds() {
        val state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 2_000L, canWrite = true)

        assertEquals("14:59", view.subtitleArg)
    }

    @Test
    fun overdue_timer_is_clamped_to_zero() {
        val state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 10_000_000L, canWrite = true)

        assertEquals("00:00", view.subtitleArg)
    }

    @Test
    fun one_hour_preset_formats_sixty_minutes() {
        val state = KeepAwakeState.active(Preset.HOUR_1, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 1_000L, canWrite = true)

        assertEquals("60:00", view.subtitleArg)
    }

    @Test
    fun missing_write_permission_maps_to_error_tile() {
        val state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        val view = TileStateMapper.map(state, nowMs = 1_000L, canWrite = false)

        assertEquals(R.string.tile_label_error, view.labelRes)
        assertEquals(R.string.tile_subtitle_permission, view.subtitleRes)
        assertEquals(R.drawable.ic_tile_error, view.iconRes)
        assertEquals(TileUiState.ERROR, view.uiState)
    }
}
