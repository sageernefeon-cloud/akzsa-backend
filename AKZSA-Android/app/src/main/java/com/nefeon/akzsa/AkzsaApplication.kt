package com.nefeon.akzsa

import android.app.Application
import com.nefeon.akzsa.diagnostics.CrashReporter

class AkzsaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
