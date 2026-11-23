package io.github.mrpixoff.keepawake.domain

import io.github.mrpixoff.keepawake.R
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import java.util.Locale

object TileStateMapper {
    fun map(state: KeepAwakeState, nowMs: Long, canWrite: Boolean): TileViewState {
        if (!canWrite) {
            return TileViewState(
                labelRes = R.string.tile_label_error,
                subtitleRes = R.string.tile_subtitle_permission,
                subtitleArg = null,
                iconRes = R.drawable.ic_tile_error,
                uiState = TileUiState.ERROR
            )
        }
        if (!state.isActive) {
            return TileViewState(
                labelRes = R.string.tile_label_inactive,
                subtitleRes = R.string.tile_subtitle_off,
                subtitleArg = null,
                iconRes = R.drawable.ic_tile_inactive,
                uiState = TileUiState.INACTIVE
            )
        }
        val deadline = state.deadlineMs
        if (deadline == null) {
            return TileViewState(
                labelRes = R.string.tile_label_active,
                subtitleRes = R.string.tile_subtitle_indefinite,
                subtitleArg = null,
                iconRes = R.drawable.ic_tile_active,
                uiState = TileUiState.ACTIVE
            )
        }
        return TileViewState(
            labelRes = R.string.tile_label_active,
            subtitleRes = R.string.tile_subtitle_remaining,
            subtitleArg = formatRemaining((deadline - nowMs).coerceAtLeast(0L)),
            iconRes = R.drawable.ic_tile_active,
            uiState = TileUiState.ACTIVE
        )
    }

    private fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs + 999L) / 1_000L
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }
}
