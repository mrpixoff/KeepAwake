package io.github.mrpixoff.keepawake.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mrpixoff.keepawake.AppContainer

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            AppContainer.controller().onScreenOff()
        }
    }
}
