package com.aviai

import android.app.Application
import android.util.Log

class AviAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AviAiApplication initialized. Device API: ${android.os.Build.VERSION.SDK_INT}")
    }

    companion object {
        const val TAG = "AviAI"
    }
}
