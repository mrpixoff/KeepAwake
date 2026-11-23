package io.github.mrpixoff.keepawake.data

interface SystemSettingsGateway {
    fun getTimeoutMs(): Int
    fun setTimeoutMs(value: Int): Boolean
}
