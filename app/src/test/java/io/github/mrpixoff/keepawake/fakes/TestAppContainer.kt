package io.github.mrpixoff.keepawake.fakes

import io.github.mrpixoff.keepawake.data.AppStateStore
import io.github.mrpixoff.keepawake.data.SystemSettingsGateway
import io.github.mrpixoff.keepawake.domain.ScreenTimeoutManager

object TestAppContainer {
    fun screenTimeoutManager(
        gateway: SystemSettingsGateway = FakeSystemSettingsGateway(),
        store: AppStateStore = FakeAppStateStore()
    ): ScreenTimeoutManager = ScreenTimeoutManager(gateway, store)
}
