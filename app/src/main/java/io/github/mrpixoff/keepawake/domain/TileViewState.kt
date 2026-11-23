package io.github.mrpixoff.keepawake.domain

enum class TileUiState { ACTIVE, INACTIVE, ERROR }

data class TileViewState(
    val labelRes: Int,
    val subtitleRes: Int,
    val subtitleArg: String?,
    val iconRes: Int,
    val uiState: TileUiState
)
