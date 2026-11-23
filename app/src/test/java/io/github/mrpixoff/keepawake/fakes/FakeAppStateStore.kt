package io.github.mrpixoff.keepawake.fakes

import io.github.mrpixoff.keepawake.core.KeepAwakeState
import io.github.mrpixoff.keepawake.data.AppStateStore

class FakeAppStateStore : AppStateStore {
    var state: KeepAwakeState = KeepAwakeState.inactive()

    override fun load(): KeepAwakeState = state

    override fun save(state: KeepAwakeState) {
        this.state = state
    }

    override fun clear() {
        state = KeepAwakeState.inactive()
    }
}
