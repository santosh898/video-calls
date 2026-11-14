package com.videocalls.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.videocalls.app.R
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.model.CallType
import com.videocalls.app.ui.MainActivity

object CallNotificationHelper {

    private const val TAG = "CallNotificationHelper"

    fun showIncomingCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        callerPhone: String,
        callType: CallType,
        isGroupCall: Boolean,
        groupId: String?
    ) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "incoming_call")
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerPhone", callerPhone)
            putExtra("callType", callType.name)
            putExtra("isGroupCall", isGroupCall)
            putExtra("groupId", groupId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = MainActivity.ACTION_ACCEPT_CALL
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerPhone", callerPhone)
            putExtra("callType", callType.name)
            putExtra("isGroupCall", isGroupCall)
            putExtra("groupId", groupId)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            (callId + "_accept").hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action Intent (handled in background without opening app UI)
        val declineIntent = Intent(context, CallNotificationReceiver::class.java).apply {
            action = CallNotificationReceiver.ACTION_DECLINE_CALL
            putExtra("callId", callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            (callId + "_decline").hashCode(),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val title = if (isGroupCall) "Group Video Call" else (if (callType == CallType.VIDEO) "Incoming Video Call" else "Incoming Audio Call")
        val content = if (callerPhone.isNotBlank()) "$callerName ($callerPhone)" else callerName

        val callerPerson = androidx.core.app.Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val notificationBuilder = NotificationCompat.Builder(context, VideoCallApp.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 1000, 1000, 1000, 1000))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)

        // Native Android CallStyle for standard Answer & Decline buttons
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            notificationBuilder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    acceptPendingIntent
                )
            )
        }

        val notification = notificationBuilder.build()

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            @Suppress("DEPRECATION")
            val screenWakeLock = pm?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "VideoCalls:IncomingCallWakeUp"
            )
            screenWakeLock?.acquire(10_000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake up screen for incoming call", e)
        }

        try {
            NotificationManagerCompat.from(context).notify(callId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
    }

    fun cancelNotification(context: Context, callId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(callId.hashCode())
    }
}
