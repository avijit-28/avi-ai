package com.aviai.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aviai.MainActivity
import com.aviai.data.UiCallRecord

object CallNotificationManager {
    const val CHANNEL_ID = "avi_ai_calls"
    private const val PREFS_NAME = "avi_ai_notifications"
    private const val KEY_NOTIFIED_CALLS = "notified_call_ids"

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Avi AI Voice Messages"
            val descriptionText = "Notifications for unattended calls answered by Avi AI"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showCallNotification(context: Context, call: UiCallRecord) {
        if (!hasNotificationPermission(context)) return

        val callerName = call.summary?.callerName ?: call.callerNumber
        val purpose = call.summary?.callPurpose ?: "New message recorded"
        val fullSummary = call.summary?.fullSummary ?: "Caller left a message."
        val urgency = call.summary?.urgency ?: "Normal"

        // Open MainActivity when tapping notification
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", call.id)
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            call.id.hashCode(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Call Back Action Intent
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.callerNumber}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            call.id.hashCode() + 1,
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
            .setContentTitle("📞 Voice Message: $callerName")
            .setContentText(purpose)
            .setSubText("Avi AI • $urgency")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("👤 Caller: $callerName (${call.callerNumber})\n🎯 Purpose: $purpose\n\n📝 Summary: $fullSummary")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Back", dialPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(call.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Notification permission denied
        }
    }

    fun showTestNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            9999,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+919876543210"))
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            10000,
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
            .setContentTitle("📞 Voice Message: Rahul Sharma")
            .setContentText("Discuss project deployment timeline")
            .setSubText("Avi AI • High Priority")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("👤 Caller: Rahul Sharma (+91 98765 43210)\n🎯 Purpose: Urgently wants to discuss deployment release before 5 PM.\n\n📝 Summary: Rahul called while you were away. He asked you to call back as soon as you are free.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Back", dialPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(9999, builder.build())
        } catch (e: SecurityException) {
        }
    }

    fun checkAndNotifyNewCalls(context: Context, calls: List<UiCallRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notified = prefs.getStringSet(KEY_NOTIFIED_CALLS, null)?.toMutableSet() ?: mutableSetOf()

        // If this is first run with existing records, seed them so we don't bombard 10 notifications at once
        if (notified.isEmpty() && calls.isNotEmpty()) {
            calls.forEach { notified.add(it.id) }
            prefs.edit().putStringSet(KEY_NOTIFIED_CALLS, notified).apply()
            return
        }

        var hasNew = false
        for (call in calls) {
            if (call.status.equals("COMPLETED", true) && !notified.contains(call.id)) {
                showCallNotification(context, call)
                notified.add(call.id)
                hasNew = true
            }
        }

        if (hasNew) {
            prefs.edit().putStringSet(KEY_NOTIFIED_CALLS, notified).apply()
        }
    }
}
