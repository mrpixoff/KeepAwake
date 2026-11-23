package io.github.mrpixoff.keepawake.core

data class KeepAwakeState(
    val isActive: Boolean,
    val preset: Preset,
    val startedAtMs: Long?,
    val deadlineMs: Long?,
    val originalTimeoutMs: Int?
) {
    companion object {
        fun inactive(): KeepAwakeState = KeepAwakeState(
            isActive = false,
            preset = Preset.INDEFINITE,
            startedAtMs = null,
            deadlineMs = null,
            originalTimeoutMs = null
        )

        fun active(
            preset: Preset,
            startedAtMs: Long,
            originalTimeoutMs: Int
        ): KeepAwakeState {
            require(originalTimeoutMs > 0) { "originalTimeoutMs must be positive" }
            return KeepAwakeState(
                isActive = true,
                preset = preset,
                startedAtMs = startedAtMs,
                deadlineMs = preset.durationMs?.let { startedAtMs + it },
                originalTimeoutMs = originalTimeoutMs
            )
        }
    }
}
