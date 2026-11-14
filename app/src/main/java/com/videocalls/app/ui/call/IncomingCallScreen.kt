package com.videocalls.app.ui.call

import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.data.util.PhoneNumberHelper
import com.videocalls.app.service.CallNotificationHelper
import com.videocalls.app.ui.theme.EmeraldCallAccept
import com.videocalls.app.ui.theme.RoseCallDecline
import kotlinx.coroutines.launch

@Composable
fun IncomingCallScreen(
    callId: String,
    callerName: String,
    callerPhone: String,
    callType: String,
    isGroupCall: Boolean,
    groupId: String?,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    signalingRepo: CallSignalingRepository = remember { CallSignalingRepository() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isVideo = callType == "VIDEO"

    // Play in-app ringtone and vibrate while ringing
    val ringtone = remember {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(context, uri).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    DisposableEffect(Unit) {
        try {
            ringtone?.play()
            val pattern = longArrayOf(0, 1000, 1000, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("IncomingCallScreen", "Error playing ringtone", e)
        }

        onDispose {
            try {
                ringtone?.stop()
                vibrator?.cancel()
            } catch (e: Exception) {}
        }
    }

    // Observe if caller cancels the call
    val callSession by signalingRepo.observeCallSession(callId).collectAsState(initial = null)
    LaunchedEffect(callSession?.status) {
        val status = callSession?.status
        if (status == CallStatus.ENDED || status == CallStatus.MISSED) {
            onDeclineCall()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = callerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callerName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (callerPhone.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = PhoneNumberHelper.formatForDisplay(callerPhone),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGroupCall) "Incoming Group Call" else (if (isVideo) "Incoming Video Call" else "Incoming Audio Call"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Accept and Decline Buttons at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        coroutineScope.launch {
                            signalingRepo.updateCallStatus(callId, CallStatus.REJECTED)
                            onDeclineCall()
                        }
                    },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = RoseCallDecline,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Decline",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Decline", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            }

            // Accept Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = {
                        coroutineScope.launch {
                            signalingRepo.updateCallStatus(callId, CallStatus.CONNECTED)
                            onAcceptCall()
                        }
                    },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = EmeraldCallAccept,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = "Accept",
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Accept", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
