package io.github.mrpixoff.keepawake.data

import android.content.Context
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset

class PrefsAppStateStore(context: Context) : AppStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        Constants.PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): KeepAwakeState {
        if (!prefs.getBoolean(Constants.PREF_IS_ACTIVE, false)) return KeepAwakeState.inactive()

        val preset = runCatching {
            Preset.entries[prefs.getInt(Constants.PREF_PRESET, 0)]
        }.getOrDefault(Preset.INDEFINITE)
        val startedAtMs = prefs.getLong(Constants.PREF_STARTED_AT_MS, -1L)
        val originalTimeoutMs = prefs.getInt(Constants.PREF_ORIGINAL_TIMEOUT_MS, 0)
        if (startedAtMs < 0L || originalTimeoutMs <= 0) return KeepAwakeState.inactive()

        val deadlineMs = prefs.getLong(Constants.PREF_DEADLINE_MS, -1L).takeIf { it > 0L }
        return KeepAwakeState(
            isActive = true,
            preset = preset,
            startedAtMs = startedAtMs,
            deadlineMs = deadlineMs,
            originalTimeoutMs = originalTimeoutMs
        )
    }

    override fun save(state: KeepAwakeState) {
        prefs.edit()
            .putBoolean(Constants.PREF_IS_ACTIVE, state.isActive)
            .putInt(Constants.PREF_PRESET, state.preset.ordinal)
            .putLong(Constants.PREF_STARTED_AT_MS, state.startedAtMs ?: -1L)
            .putLong(Constants.PREF_DEADLINE_MS, state.deadlineMs ?: -1L)
            .putInt(Constants.PREF_ORIGINAL_TIMEOUT_MS, state.originalTimeoutMs ?: 0)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
