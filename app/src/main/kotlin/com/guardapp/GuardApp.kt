// GuardApp.kt
package com.guardapp

import android.app.Application
import com.guardapp.data.EventLog
import com.guardapp.data.Settings

class GuardApp : Application() {
    lateinit var settings: Settings
    lateinit var eventLog: EventLog

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings.getInstance(this)
        eventLog = EventLog.getInstance(this)
    }

    companion object {
        lateinit var instance: GuardApp
            private set
    }
}
