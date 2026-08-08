package com.anxietywatch.wear

import android.app.Application
import com.anxietywatch.wear.runtime.WearRuntime

class AnxietyWatchApplication : Application() {
    lateinit var runtime: WearRuntime
        private set

    override fun onCreate() {
        super.onCreate()
        runtime = WearRuntime(this)
    }
}
