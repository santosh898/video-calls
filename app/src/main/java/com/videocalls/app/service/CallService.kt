package com.videocalls.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.videocalls.app.R
import com.videocalls.app.VideoCallApp
import com.videocalls.app.ui.MainActivity

private const val TAG = "CallService"

class CallService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VideoCalls:CallWakeLock").apply {
                acquire(2 * 60 * 60 * 1000L) // 2 hours max
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_CALL_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: ""
        val displayName = intent?.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Call"
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, true) ?: true

        val notification = createOngoingCallNotification(callId, displayName, isVideo)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (isVideo) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fallback startForeground failed: ${fallbackEx.message}", fallbackEx)
            }
        }

        return START_STICKY
    }

    private fun createOngoingCallNotification(callId: String, displayName: String, isVideo: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_STOP_CALL_SERVICE
        }
        val endPendingIntent = PendingIntent.getService(
            this,
            1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VideoCallApp.CHANNEL_ONGOING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isVideo) "Ongoing Video Call" else "Ongoing Audio Call")
            .setContentText(displayName)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakeLock", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START_CALL_SERVICE = "ACTION_START_CALL_SERVICE"
        const val ACTION_STOP_CALL_SERVICE = "ACTION_STOP_CALL_SERVICE"

        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_DISPLAY_NAME = "EXTRA_DISPLAY_NAME"
        const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"

        fun start(context: Context, callId: String, displayName: String, isVideo: Boolean) {
            try {
                val intent = Intent(context, CallService::class.java).apply {
                    action = ACTION_START_CALL_SERVICE
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                    putExtra(EXTRA_IS_VIDEO, isVideo)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CallService::class.java).apply {
                    action = ACTION_STOP_CALL_SERVICE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop CallService: ${e.message}", e)
            }
        }
    }
}
