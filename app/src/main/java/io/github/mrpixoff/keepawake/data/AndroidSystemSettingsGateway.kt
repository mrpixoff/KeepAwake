package io.github.mrpixoff.keepawake.data

import android.content.Context
import android.provider.Settings
import io.github.mrpixoff.keepawake.core.Constants

class AndroidSystemSettingsGateway(context: Context) : SystemSettingsGateway {
    private val resolver = context.applicationContext.contentResolver

    override fun getTimeoutMs(): Int =
        Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, Constants.DEFAULT_TIMEOUT_MS)

    override fun setTimeoutMs(value: Int): Boolean =
        runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, value) }
            .getOrDefault(false)
}
