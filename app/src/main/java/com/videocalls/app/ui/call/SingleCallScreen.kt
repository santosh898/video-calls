package com.videocalls.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
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
import androidx.core.content.ContextCompat
import com.videocalls.app.data.model.CallSession
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.data.util.PhoneNumberHelper
import com.videocalls.app.service.CallService
import com.videocalls.app.ui.home.InitialsAvatar
import com.videocalls.app.ui.theme.RoseCallDecline
import com.videocalls.app.webrtc.WebRtcClient
import com.videocalls.app.webrtc.WebRtcSurfaceView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

@Composable
fun SingleCallScreen(
    callId: String,
    currentUid: String,
    isOutgoing: Boolean,
    targetName: String,
    targetPhone: String,
    isVideo: Boolean,
    onCallEnded: () -> Unit,
    signalingRepo: CallSignalingRepository = remember { CallSignalingRepository() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            !isVideo || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = !isVideo || (perms[Manifest.permission.CAMERA] == true)
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) permissions.add(Manifest.permission.CAMERA)
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    if (!hasAudioPermission || (!hasCameraPermission && isVideo)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Camera & Microphone permissions required for calls",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (isVideo) list.add(Manifest.permission.CAMERA)
                    permissionsLauncher.launch(list.toTypedArray())
                }) {
                    Text("Grant Permissions")
                }
            }
        }
        return
    }

    val rtcClient = remember { WebRtcClient(context) }
    val remoteVideoTracks by rtcClient.remoteVideoTracks.collectAsState()
    val localVideoTrack by rtcClient.localVideoTrackFlow.collectAsState()
    val isMicMuted by rtcClient.isMicMuted.collectAsState()
    val isVideoEnabled by rtcClient.isVideoEnabled.collectAsState()
    val isSpeakerOn by rtcClient.isSpeakerOn.collectAsState()

    val callSession by signalingRepo.observeCallSession(callId).collectAsState(initial = null)
    var isConnected by remember { mutableStateOf(false) }
    var offerCreated by remember { mutableStateOf(false) }
    var isEnded by remember { mutableStateOf(false) }

    // Call controls auto-hide & visibility state
    var areControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun resetControlsTimer() {
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(areControlsVisible, lastInteractionTime) {
        if (areControlsVisible) {
            delay(4000L)
            areControlsVisible = false
        }
    }

    fun endCallInternal() {
        if (isEnded) return
        isEnded = true
        coroutineScope.launch {
            signalingRepo.updateCallStatus(callId, CallStatus.ENDED)
            CallService.stop(context)
            rtcClient.close()
            onCallEnded()
        }
    }

    BackHandler {
        endCallInternal()
    }

    // React to remote call session updates
    LaunchedEffect(callSession?.status) {
        val status = callSession?.status
        if (status == CallStatus.ENDED || status == CallStatus.REJECTED || status == CallStatus.MISSED) {
            if (!isEnded) {
                isEnded = true
                CallService.stop(context)
                rtcClient.close()
                onCallEnded()
            }
        }
    }

    // Signaling and WebRTC Lifecycle
    LaunchedEffect(callId) {
        rtcClient.startLocalMedia(isAudioOnly = !isVideo)
        CallService.start(context, callId, targetName, isVideo)

        // Forward local ICE candidates to Firestore
        rtcClient.onLocalIceCandidate = { _, candidate ->
            coroutineScope.launch {
                signalingRepo.sendCandidate(callId, isCaller = isOutgoing, candidate = candidate)
            }
        }

        rtcClient.onPeerConnected = {
            isConnected = true
            coroutineScope.launch {
                signalingRepo.updateCallStatus(callId, CallStatus.CONNECTED)
            }
        }

        rtcClient.onPeerDisconnected = {
            isConnected = false
        }

        // Listen for remote ICE candidates
        launch {
            signalingRepo.observeRemoteCandidates(callId, isCaller = isOutgoing).collect { candidate ->
                rtcClient.addRemoteIceCandidate(targetName, candidate)
            }
        }

        if (isOutgoing) {
            // Caller: create and publish offer
            if (!offerCreated) {
                offerCreated = true
                rtcClient.createOffer(targetName) { offer ->
                    coroutineScope.launch {
                        signalingRepo.sendOffer(callId, offer)
                    }
                }
            }
            // Caller: listen for receiver's answer
            launch {
                signalingRepo.observeAnswer(callId).collect { answer ->
                    if (answer != null) {
                        rtcClient.setRemoteAnswer(targetName, answer)
                    }
                }
            }
        } else {
            // Receiver: listen for caller's offer, create answer and publish
            launch {
                signalingRepo.observeOffer(callId).collect { offer ->
                    if (offer != null) {
                        rtcClient.createAnswer(targetName, offer) { answer ->
                            coroutineScope.launch {
                                signalingRepo.sendAnswer(callId, answer)
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isEnded) {
                isEnded = true
                CallService.stop(context)
                rtcClient.close()
            }
        }
    }

    val remoteTrack = remoteVideoTracks.values.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
                if (areControlsVisible) {
                    resetControlsTimer()
                }
            }
    ) {
        // Remote View
        if (isVideo && remoteTrack != null) {
            WebRtcSurfaceView(
                modifier = Modifier.fillMaxSize(),
                videoTrack = remoteTrack,
                eglBase = rtcClient.eglBase,
                isOverlay = false
            )
        } else {
            // Audio call or waiting for video view
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = targetName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = PhoneNumberHelper.formatForDisplay(targetPhone),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isConnected) "Connected" else (if (isOutgoing) "Calling…" else "Connecting…"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isConnected) Color(0xFF10B981) else Color(0xFF38BDF8)
                    )
                }
            }
        }

        // Local Preview PIP in top right (Video only)
        if (isVideo && isVideoEnabled && localVideoTrack != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    .size(width = 110.dp, height = 155.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                WebRtcSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    videoTrack = localVideoTrack,
                    eglBase = rtcClient.eglBase,
                    isMirror = true,
                    isOverlay = true
                )
            }
        }

        // Top Status Bar (Overlay)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 44.dp, start = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "HD Call" else "Connecting",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom Controls Bar
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it },
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mic Mute Toggle
                    FilledIconButton(
                        onClick = {
                            resetControlsTimer()
                            rtcClient.toggleMic()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isMicMuted) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (isMicMuted) Color(0xFFEF4444) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = "Mic"
                        )
                    }

                    // Camera Flip
                    if (isVideo) {
                        FilledIconButton(
                            onClick = {
                                resetControlsTimer()
                                rtcClient.flipCamera()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Flip Camera"
                            )
                        }

                        // Video Track Toggle
                        FilledIconButton(
                            onClick = {
                                resetControlsTimer()
                                rtcClient.toggleVideo()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (!isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                contentColor = if (!isVideoEnabled) Color(0xFFEF4444) else Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                contentDescription = "Toggle Video"
                            )
                        }
                    }

                    // Speakerphone Toggle
                    FilledIconButton(
                        onClick = {
                            resetControlsTimer()
                            rtcClient.toggleSpeakerphone()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSpeakerOn) Color(0xFF0284C7).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (isSpeakerOn) Color(0xFF38BDF8) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = "Speaker"
                        )
                    }

                    // End Call (Rose red circular button)
                    FilledIconButton(
                        onClick = { endCallInternal() },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RoseCallDecline,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
