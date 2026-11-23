package io.github.mrpixoff.keepawake.core

enum class Preset(val durationMs: Long?) {
    INDEFINITE(null),
    MIN_15(900_000L),
    MIN_30(1_800_000L),
    HOUR_1(3_600_000L);

    companion object {
        const val DEFAULT = "INDEFINITE"
    }
}
