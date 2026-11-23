package io.github.mrpixoff.keepawake.fakes

import android.content.Context
import io.github.mrpixoff.keepawake.domain.NotificationGuard
import io.github.mrpixoff.keepawake.domain.WriteSettingsGuard

class FakeWriteSettingsGuard(var canWrite: Boolean = true) : WriteSettingsGuard {
    override fun canWrite(context: Context): Boolean = canWrite
}

class FakeNotificationGuard(var granted: Boolean = true) : NotificationGuard {
    override fun isGranted(context: Context): Boolean = granted
}
