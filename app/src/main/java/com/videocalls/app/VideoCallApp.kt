package com.videocalls.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.videocalls.app.data.local.LiveSyncManager

class VideoCallApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        LiveSyncManager.getInstance(this).startLiveSync()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel for Incoming Calls (High Priority with ringtone)
            val incomingRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent incoming audio and video call alerts"
                setSound(incomingRingtone, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Channel for Ongoing Call Foreground Service
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_CALLS,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status for active video/audio call"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    companion object {
        const val CHANNEL_INCOMING_CALLS = "incoming_calls_channel"
        const val CHANNEL_ONGOING_CALLS = "ongoing_calls_channel"

        lateinit var instance: VideoCallApp
            private set
    }
}
