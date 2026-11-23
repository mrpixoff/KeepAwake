package io.github.mrpixoff.keepawake.fakes

import io.github.mrpixoff.keepawake.data.SystemSettingsGateway

class FakeSystemSettingsGateway(var value: Int = 600_000) : SystemSettingsGateway {
    val setCalls = mutableListOf<Int>()
    var getCalls: Int = 0
        private set
    var failNextSet: Boolean = false

    override fun getTimeoutMs(): Int {
        getCalls++
        return value
    }

    override fun setTimeoutMs(value: Int): Boolean {
        if (failNextSet) {
            failNextSet = false
            return false
        }
        setCalls.add(value)
        this.value = value
        return true
    }
}
