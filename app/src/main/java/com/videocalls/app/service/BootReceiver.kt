package com.videocalls.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.videocalls.app.data.repository.AuthRepository

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Device booted or app updated ($action), checking if CallListenerService should start")
            val authRepo = AuthRepository(context)
            if (authRepo.isSimRegistered()) {
                Log.d(TAG, "User registered, starting CallListenerService")
                CallListenerService.start(context)
            }
        }
    }
}
