package io.github.mrpixoff.keepawake.core

object Constants {
    const val PREF_IS_ACTIVE = "is_active"
    const val PREF_PRESET = "preset"
    const val PREF_STARTED_AT_MS = "started_at_ms"
    const val PREF_DEADLINE_MS = "deadline_ms"
    const val PREF_ORIGINAL_TIMEOUT_MS = "original_timeout_ms"
    const val PREFERENCES_NAME = "keepawake_prefs"
    const val UI_PREFERENCES_NAME = "keepawake_ui_prefs"
    const val PREF_SELECTED_PRESET = "selected_preset"
    const val PREF_ONBOARDING_COMPLETED = "onboarding_completed"

    const val ACTION_START = "io.github.mrpixoff.keepawake.action.START"
    const val ACTION_STOP = "io.github.mrpixoff.keepawake.action.STOP"
    const val ACTION_TIMER_EXPIRED = "io.github.mrpixoff.keepawake.action.TIMER_EXPIRED"

    const val NOTIFICATION_CHANNEL_ID = "keepawake_fgs"
    const val WAKE_LOCK_TAG = "KeepAwake::ScreenBright"
    const val INDEFINITE_TIMEOUT_MS = Int.MAX_VALUE.toLong()
    const val TILE_REFRESH_INTERVAL_MS = 30_000L
    const val LOW_BATTERY_THRESHOLD_PERCENT = 15
    const val DEFAULT_TIMEOUT_MS = 600_000
    const val TIMER_TICK_INTERVAL_MS = 1_000L
}
