package com.aviai.telephony

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.aviai.AviAiApplication.Companion.TAG

class AviInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "AviInCallService created and bound by Android Telecom framework.")
        CallController.logEvent("AviInCallService bound by Android Telecom framework.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "AviInCallService destroyed.")
        CallController.logEvent("AviInCallService unbound.")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "InCallService.onCallAdded: $call")
        CallController.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "InCallService.onCallRemoved: $call")
        CallController.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let {
            val routeStr = when (it.route) {
                CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
                CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
                CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
                CallAudioState.ROUTE_WIRED_HEADSET -> "WIRED_HEADSET"
                else -> "OTHER(${it.route})"
            }
            CallController.logEvent("Call audio route changed -> $routeStr (Muted: ${it.isMuted})")
        }
    }

    companion object {
        var instance: AviInCallService? = null
            private set
    }
}
