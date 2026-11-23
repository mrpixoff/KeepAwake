package io.github.mrpixoff.keepawake

import io.github.mrpixoff.keepawake.core.Constants
import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.core.Preset
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager
import io.github.mrpixoff.keepawake.fakes.FakeAppStateStore
import io.github.mrpixoff.keepawake.fakes.FakeSystemSettingsGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeoutManagerTest {

    private val gateway = FakeSystemSettingsGateway(value = 6_000)
    private val store = FakeAppStateStore()
    private val manager = ScreenTimeoutManager(gateway, store)

    @Test
    fun captureOriginalIfNeeded_returns_current_timeout_when_nothing_saved() {
        assertEquals(6_000, manager.captureOriginalIfNeeded())
    }

    @Test
    fun captureOriginalIfNeeded_returns_saved_original_without_touching_gateway() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 7_000)

        assertEquals(7_000, manager.captureOriginalIfNeeded())
        assertTrue(gateway.getCalls == 0)
    }

    @Test
    fun captureOriginalIfNeeded_returns_null_for_non_positive_system_value() {
        gateway.value = 0

        assertEquals(null, manager.captureOriginalIfNeeded())
    }

    @Test
    fun applyActiveTimeout_writes_indefinite_value() {
        assertTrue(manager.applyActiveTimeout())
        assertEquals(listOf(Constants.INDEFINITE_TIMEOUT_MS.toInt()), gateway.setCalls)
    }

    @Test
    fun applyActiveTimeout_propagates_gateway_failure() {
        gateway.failNextSet = true

        assertFalse(manager.applyActiveTimeout())
    }

    @Test
    fun restoreOriginal_writes_saved_original_once_and_is_noop_after_clear() {
        store.state = KeepAwakeState.active(Preset.MIN_15, startedAtMs = 1_000L, originalTimeoutMs = 6_000)

        assertTrue(manager.restoreOriginal())
        assertEquals(listOf(6_000), gateway.setCalls)

        store.clear()
        assertTrue(manager.restoreOriginal())
        assertEquals(listOf(6_000), gateway.setCalls)
    }

    @Test
    fun restoreOriginal_without_saved_value_does_not_write() {
        assertTrue(manager.restoreOriginal())
        assertTrue(gateway.setCalls.isEmpty())
    }

    @Test
    fun currentTimeoutMs_delegates_to_gateway() {
        assertEquals(6_000, manager.currentTimeoutMs())
    }
}
