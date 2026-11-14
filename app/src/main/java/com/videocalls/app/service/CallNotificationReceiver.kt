package com.videocalls.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "CallNotifReceiver"

class CallNotificationReceiver : BroadcastReceiver() {

    private val signalingRepo by lazy { CallSignalingRepository() }
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val callId = intent?.getStringExtra("callId") ?: return

        Log.d(TAG, "Received action: $action for callId: $callId")

        if (action == ACTION_DECLINE_CALL) {
            CallNotificationHelper.cancelNotification(context, callId)
            val pendingResult = goAsync()
            scope.launch {
                try {
                    signalingRepo.updateCallStatus(callId, CallStatus.REJECTED)
                    Log.d(TAG, "Call $callId marked as REJECTED")
                } catch (e: Exception) {
                    Log.e(TAG, "Error rejecting call $callId", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_DECLINE_CALL = "com.videocalls.app.ACTION_DECLINE_CALL"
    }
}
