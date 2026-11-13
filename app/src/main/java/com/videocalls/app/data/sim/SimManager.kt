package com.videocalls.app.data.sim

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.videocalls.app.data.util.PhoneNumberHelper

private const val TAG = "SimManager"

class SimManager(private val context: Context) {

    fun hasPhonePermission(): Boolean {
        val numbersGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED

        val stateGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        return numbersGranted || stateGranted
    }

    /**
     * Attempts to read phone number directly from SIM card chip
     */
    fun detectSimPhoneNumber(): String? {
        if (!hasPhonePermission()) return null

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return null

            val activeSubscriptions: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            if (activeSubscriptions.isNullOrEmpty()) return null

            for (info in activeSubscriptions) {
                val rawNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        subscriptionManager.getPhoneNumber(info.subscriptionId)
                    } catch (e: Exception) {
                        info.number
                    }
                } else {
                    info.number
                }

                if (!rawNumber.isNullOrBlank()) {
                    val cleanNumber = PhoneNumberHelper.cleanIndianNumber(rawNumber)
                    if (cleanNumber.length == 10) {
                        Log.d(TAG, "Detected SIM phone number from chip: $cleanNumber")
                        return cleanNumber
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission missing to read SIM number", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting SIM phone number", e)
        }

        return null
    }

    /**
     * Triggers Google Play Services 1-Tap SIM Card Sheet (for SIM cards where carrier left chip storage blank)
     */
    fun requestPhoneNumberHint(
        activity: Activity,
        onIntentSenderReady: (IntentSenderRequest) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        Identity.getSignInClient(activity)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { pendingIntent ->
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                onIntentSenderReady(intentSenderRequest)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Phone number hint request failed", e)
                onError(e)
            }
    }

    fun parsePhoneNumberFromHint(data: android.content.Intent?): String? {
        if (data == null) return null
        return try {
            val phoneNumber = Identity.getSignInClient(context).getPhoneNumberFromIntent(data)
            val clean = PhoneNumberHelper.cleanIndianNumber(phoneNumber)
            if (clean.length == 10) clean else null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting phone number from hint", e)
            null
        }
    }
}
