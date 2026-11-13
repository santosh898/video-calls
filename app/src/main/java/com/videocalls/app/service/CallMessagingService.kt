package com.videocalls.app.service

import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.videocalls.app.R
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.model.CallType
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "CallMessagingService"

class CallMessagingService : FirebaseMessagingService() {

    private val authRepository by lazy { AuthRepository() }
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        serviceScope.launch {
            authRepository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: ""
            if (type == "INCOMING_CALL") {
                val callId = data["callId"] ?: ""
                val callerName = data["callerName"] ?: "Unknown"
                val callerPhone = data["callerPhone"] ?: ""
                val callTypeStr = data["callType"] ?: "VIDEO"
                val isGroupCall = data["isGroupCall"]?.toBoolean() ?: false
                val groupId = data["groupId"]

                CallNotificationHelper.showIncomingCallNotification(
                    context = this,
                    callId = callId,
                    callerName = callerName,
                    callerPhone = callerPhone,
                    callType = if (callTypeStr == "AUDIO") CallType.AUDIO else CallType.VIDEO,
                    isGroupCall = isGroupCall,
                    groupId = groupId
                )
            }
        }
    }

    private fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        callerPhone: String,
        callType: CallType,
        isGroupCall: Boolean,
        groupId: String?
    ) {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
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
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action Intent
        val acceptIntent = Intent(this, MainActivity::class.java).apply {
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
            this,
            (callId + "_accept").hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action Intent
        val declineIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_DECLINE_CALL
            putExtra("callId", callId)
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this,
            (callId + "_decline").hashCode(),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val title = if (isGroupCall) "Group Video Call" else (if (callType == CallType.VIDEO) "Incoming Video Call" else "Incoming Audio Call")
        val content = if (callerPhone.isNotBlank()) "$callerName ($callerPhone)" else callerName

        val notification = NotificationCompat.Builder(this, VideoCallApp.CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 1000, 1000, 1000, 1000))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        }
    }
}
