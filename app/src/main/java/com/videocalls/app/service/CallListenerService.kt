package com.videocalls.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.videocalls.app.R
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

private const val TAG = "CallListenerService"
private const val NOTIFICATION_ID = 9001

class CallListenerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val authRepo by lazy { AuthRepository(applicationContext) }
    private val signalingRepo by lazy { CallSignalingRepository() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var wakeLock: PowerManager.WakeLock? = null
    private var listeningJob: Job? = null
    private var currentRingingCallId: String? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoCalls:CallListenerLock")
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours renewable
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock in CallListenerService", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex2: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${ex2.message}", ex2)
            }
        }

        startListeningForCalls()

        return START_STICKY
    }

    private fun startListeningForCalls() {
        if (listeningJob?.isActive == true) return

        listeningJob = serviceScope.launch {
            val myPhone = authRepo.getLocalPhoneNumber()
            var myUid = auth.currentUser?.uid

            if (myUid.isNullOrBlank()) {
                try {
                    val authResult = auth.signInAnonymously().await()
                    myUid = authResult.user?.uid
                } catch (e: Exception) {
                    Log.e(TAG, "Anonymous sign-in failed in listener service", e)
                }
            }

            Log.d(TAG, "Starting incoming call observer for phone: $myPhone, uid: $myUid")

            signalingRepo.observeIncomingCalls(receiverPhone = myPhone, receiverUid = myUid).collect { incomingCall ->
                if (incomingCall != null && incomingCall.status == CallStatus.RINGING) {
                    Log.d(TAG, "Incoming call received in service: ${incomingCall.id}, isGroup=${incomingCall.isGroupCall}")
                    currentRingingCallId = incomingCall.id
                    CallNotificationHelper.showIncomingCallNotification(
                        context = applicationContext,
                        callId = incomingCall.id,
                        callerName = incomingCall.callerName.ifBlank { if (incomingCall.isGroupCall) "Group Call" else "Caller" },
                        callerPhone = incomingCall.callerPhone,
                        callType = incomingCall.callType,
                        isGroupCall = incomingCall.isGroupCall,
                        groupId = incomingCall.groupId
                    )
                } else {
                    // Call ended, declined, or cancelled by caller
                    if (currentRingingCallId != null) {
                        Log.d(TAG, "Call ended or cancelled, dismissing notification for $currentRingingCallId")
                        CallNotificationHelper.cancelNotification(applicationContext, currentRingingCallId!!)
                        currentRingingCallId = null
                    }
                }
            }
        }
    }

    private fun createServiceNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VideoCallApp.CHANNEL_LISTENER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VideoCalls Active")
            .setContentText("Listening for incoming calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed from Recents, scheduling restart of CallListenerService")
        // Schedule immediate restart if user closes or swipes app away
        try {
            val restartIntent = Intent(applicationContext, CallListenerService::class.java).also {
                it.setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                2,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule restart on task removed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        Log.d(TAG, "CallListenerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, CallListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "CallListenerService started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallListenerService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CallListenerService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop CallListenerService", e)
            }
        }
    }
}
