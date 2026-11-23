package io.github.mrpixoff.keepawake.fakes

import io.github.mrpixoff.keepawake.core.Clock

class FakeClock(var nowMs: Long = 0L) : Clock {
    override fun nowMs(): Long = nowMs

    fun advanceBy(deltaMs: Long) {
        nowMs += deltaMs
    }
}
