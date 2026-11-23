package io.github.mrpixoff.keepawake.domain

import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.data.AppStateStore
import io.github.mrpixoff.keepawake.data.SystemSettingsGateway

/**
 * Isolated, device-independent logic around Settings.System.SCREEN_OFF_TIMEOUT.
 * All Android API calls are behind [SystemSettingsGateway], so this class is
 * fully unit-testable with a fake gateway.
 */
class ScreenTimeoutManager(
    private val gateway: SystemSettingsGateway,
    private val store: AppStateStore
) {
    fun captureOriginalIfNeeded(): Int? {
        val saved = store.load().originalTimeoutMs
        if (saved != null) return saved
        return gateway.getTimeoutMs().takeIf { it > 0 }
    }

    fun applyActiveTimeout(): Boolean {
        val value = Constants.INDEFINITE_TIMEOUT_MS
        if (value <= 0L || value > Int.MAX_VALUE) return false
        return gateway.setTimeoutMs(value.toInt())
    }

    fun restoreOriginal(): Boolean {
        val original = store.load().originalTimeoutMs ?: return true
        return gateway.setTimeoutMs(original)
    }

    fun hasSavedOriginal(): Boolean = store.load().originalTimeoutMs != null

    fun currentTimeoutMs(): Int = gateway.getTimeoutMs()
}
