package io.github.mrpixoff.keepawake

import android.app.Application

class KeepAwakeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(applicationContext)
        AppContainer.controller().restoreOnProcessStart()
    }
}
