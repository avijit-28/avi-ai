package com.aviai.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class DiagnosticStatus(
    val isDefaultDialer: Boolean,
    val hasPhoneStatePermission: Boolean,
    val hasRecordAudioPermission: Boolean,
    val hasPostNotificationsPermission: Boolean,
    val deviceModel: String,
    val osVersion: String,
    val sdkInt: Int
) {
    val isReadyForInCallService: Boolean
        get() = isDefaultDialer && hasPhoneStatePermission && hasRecordAudioPermission
}

object CallCompatibilityChecker {

    fun checkStatus(context: Context): DiagnosticStatus {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            false
        }

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasRecordAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return DiagnosticStatus(
            isDefaultDialer = isDefaultDialer,
            hasPhoneStatePermission = hasPhoneState,
            hasRecordAudioPermission = hasRecordAudio,
            hasPostNotificationsPermission = hasNotifications,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT
        )
    }
}
