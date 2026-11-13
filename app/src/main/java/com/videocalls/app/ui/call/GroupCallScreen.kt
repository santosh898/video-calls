package com.videocalls.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.service.CallService
import com.videocalls.app.ui.theme.RoseCallDecline
import com.videocalls.app.webrtc.WebRtcClient
import com.videocalls.app.webrtc.WebRtcSurfaceView
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

@Composable
fun GroupCallScreen(
    callId: String,
    groupId: String,
    groupName: String,
    currentUid: String,
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
            Text("Permissions required for group call", color = Color.White)
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
    val participants = callSession?.activeParticipants ?: emptyList()
    var isEnded by remember { mutableStateOf(false) }

    fun leaveCallInternal() {
        if (isEnded) return
        isEnded = true
        coroutineScope.launch {
            signalingRepo.leaveCall(callId, currentUid, groupId)
            CallService.stop(context)
            rtcClient.close()
            onCallEnded()
        }
    }

    BackHandler {
        leaveCallInternal()
    }

    LaunchedEffect(callSession?.status) {
        if (callSession?.status == CallStatus.ENDED) {
            if (!isEnded) {
                isEnded = true
                CallService.stop(context)
                rtcClient.close()
                onCallEnded()
            }
        }
    }

    // Initialize media and join room
    LaunchedEffect(callId) {
        rtcClient.startLocalMedia(isAudioOnly = !isVideo)
        CallService.start(context, callId, groupName, isVideo)
        signalingRepo.joinCall(callId, currentUid)

        // Local candidates
        rtcClient.onLocalIceCandidate = { targetUid, candidate ->
            coroutineScope.launch {
                signalingRepo.sendIceCandidate(callId, currentUid, targetUid, candidate)
            }
        }

        // Listen for candidates
        launch {
            signalingRepo.observeIncomingCandidates(callId, currentUid).collect { model ->
                val iceCandidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.sdp)
                rtcClient.addRemoteIceCandidate(model.senderUid, iceCandidate)
            }
        }

        // Listen for signals (offers/answers)
        launch {
            signalingRepo.observeIncomingSignals(callId, currentUid).collect { signal ->
                val type = signal["type"] as? String
                val sdpStr = signal["sdp"] as? String
                val senderUid = signal["senderUid"] as? String ?: ""

                if (type == "offer" && sdpStr != null) {
                    val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                    rtcClient.createAnswer(senderUid, remoteSdp) { answer ->
                        coroutineScope.launch {
                            signalingRepo.sendAnswer(callId, currentUid, senderUid, answer)
                        }
                    }
                } else if (type == "answer" && sdpStr != null) {
                    val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                    rtcClient.setRemoteAnswer(senderUid, remoteSdp)
                }
            }
        }
    }

    // Mesh connection establishment:
    // Whenever participants list changes, connect with peers.
    // Deterministic tie-breaker: smaller UID creates offer to larger UID.
    LaunchedEffect(participants) {
        val otherPeers = participants.filter { it != currentUid }
        for (peerUid in otherPeers) {
            if (currentUid < peerUid) {
                // We initiate offer
                rtcClient.createOffer(peerUid) { offer ->
                    coroutineScope.launch {
                        signalingRepo.sendOffer(callId, currentUid, peerUid, offer)
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

    // Render Tiles Grid
    // Total tiles = 1 (local) + remote peers
    val remotePeers = (participants.filter { it != currentUid } + remoteVideoTracks.keys).distinct()
    val totalTiles = remotePeers.size + 1
    val gridColumns = if (totalTiles <= 2) 1 else 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${participants.size} participants · Mesh P2P",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "LIVE",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Video Grid (Max 8 participants)
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Tile
                item {
                    ParticipantTile(
                        title = "You",
                        videoTrack = if (isVideo && isVideoEnabled) localVideoTrack else null,
                        eglBase = rtcClient.eglBase,
                        isMirror = true,
                        isMuted = isMicMuted
                    )
                }

                // Remote Tiles
                items(remotePeers, key = { it }) { peerUid ->
                    val videoTrack = remoteVideoTracks[peerUid]
                    ParticipantTile(
                        title = "Participant",
                        videoTrack = videoTrack,
                        eglBase = rtcClient.eglBase,
                        isMirror = false,
                        isMuted = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // Bottom Controls Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.9f),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FilledIconButton(
                    onClick = { rtcClient.toggleMic() },
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

                if (isVideo) {
                    FilledIconButton(
                        onClick = { rtcClient.flipCamera() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Cameraswitch, contentDescription = "Flip")
                    }

                    FilledIconButton(
                        onClick = { rtcClient.toggleVideo() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (!isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (!isVideoEnabled) Color(0xFFEF4444) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = "Video"
                        )
                    }
                }

                FilledIconButton(
                    onClick = { rtcClient.toggleSpeakerphone() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isSpeakerOn) Color(0xFF0284C7).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
                        contentColor = if (isSpeakerOn) Color(0xFF38BDF8) else Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                        contentDescription = "Speaker"
                    )
                }

                FilledIconButton(
                    onClick = { leaveCallInternal() },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = RoseCallDecline,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Leave Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ParticipantTile(
    title: String,
    videoTrack: VideoTrack?,
    eglBase: org.webrtc.EglBase,
    isMirror: Boolean = false,
    isMuted: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (videoTrack != null) {
                WebRtcSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    videoTrack = videoTrack,
                    eglBase = eglBase,
                    isMirror = isMirror,
                    isOverlay = true
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Bottom overlay with name & mic status
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                if (isMuted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.MicOff,
                        contentDescription = "Muted",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
