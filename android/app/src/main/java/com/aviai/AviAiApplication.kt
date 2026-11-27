package com.aviai

import android.app.Application
import android.util.Log
import com.aviai.telephony.CallNotificationManager

class AviAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AviAiApplication initialized. Device API: ${android.os.Build.VERSION.SDK_INT}")
        CallNotificationManager.initChannel(this)
    }

    companion object {
        const val TAG = "AviAI"
    }
}
