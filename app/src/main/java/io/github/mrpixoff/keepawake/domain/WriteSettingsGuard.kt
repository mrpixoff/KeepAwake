package io.github.mrpixoff.keepawake.domain

import android.content.Context
import android.provider.Settings

interface WriteSettingsGuard {
    fun canWrite(context: Context): Boolean
}

class AndroidWriteSettingsGuard : WriteSettingsGuard {
    override fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)
}
