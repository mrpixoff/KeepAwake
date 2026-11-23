package io.github.mrpixoff.keepawake.data

import io.github.mrpixoff.keepawake.core.KeepAwakeState

interface AppStateStore {
    fun load(): KeepAwakeState
    fun save(state: KeepAwakeState)
    fun clear()
}
