package io.github.mrpixoff.keepawake.core

interface Clock {
    fun nowMs(): Long
}

class SystemClockImpl : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
