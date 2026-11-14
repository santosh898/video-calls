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
}
