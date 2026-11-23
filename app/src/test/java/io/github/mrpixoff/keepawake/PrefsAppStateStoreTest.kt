package io.github.mrpixoff.keepawake

import android.content.Context
import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.data.PrefsAppStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrefsAppStateStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = PrefsAppStateStore(context)
    private val prefs = context.getSharedPreferences(Constants.PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Test
    fun load_returns_inactive_state_when_nothing_saved() {
        assertEquals(KeepAwakeState.inactive(), store.load())
    }

    @Test
    fun save_and_load_round_trip_active_state() {
        val state = KeepAwakeState.active(
            preset = Preset.MIN_30,
            startedAtMs = 1_000L,
            originalTimeoutMs = 6_000
        )

        store.save(state)

        assertEquals(state, store.load())
        assertEquals(1_801_000L, store.load().deadlineMs)
    }

    @Test
    fun clear_removes_saved_state() {
        store.save(KeepAwakeState.active(Preset.HOUR_1, 1_000L, 6_000))

        store.clear()

        assertEquals(KeepAwakeState.inactive(), store.load())
    }

    @Test
    fun load_falls_back_to_indefinite_for_corrupt_preset_ordinal() {
        prefs.edit()
            .putBoolean(Constants.PREF_IS_ACTIVE, true)
            .putInt(Constants.PREF_PRESET, 999)
            .putLong(Constants.PREF_STARTED_AT_MS, 1_000L)
            .putLong(Constants.PREF_DEADLINE_MS, -1L)
            .putInt(Constants.PREF_ORIGINAL_TIMEOUT_MS, 6_000)
            .commit()

        val state = store.load()

        assertTrue(state.isActive)
        assertEquals(Preset.INDEFINITE, state.preset)
        assertEquals(null, state.deadlineMs)
    }

    @Test
    fun load_returns_inactive_when_original_timeout_is_missing() {
        prefs.edit()
            .putBoolean(Constants.PREF_IS_ACTIVE, true)
            .putInt(Constants.PREF_PRESET, Preset.MIN_15.ordinal)
            .putLong(Constants.PREF_STARTED_AT_MS, 1_000L)
            .commit()

        assertEquals(KeepAwakeState.inactive(), store.load())
    }
}
