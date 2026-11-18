package com.videocalls.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.videocalls.app.data.local.AppDatabaseHelper
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.service.CallService
import com.videocalls.app.ui.interactive.*
import com.videocalls.app.ui.theme.RoseCallDecline
import com.videocalls.app.webrtc.WebRtcClient
import com.videocalls.app.webrtc.WebRtcSurfaceView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
    val dbHelper = remember { AppDatabaseHelper.getInstance(context) }
    val contacts by dbHelper.contactsFlow.collectAsState()

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

    // Interactive & Toddler Features
    var currentFilter by remember { mutableStateOf(InteractiveFilterType.NONE) }
    var currentGame by remember { mutableStateOf(InteractiveGameType.NONE) }
    var filterTarget by remember { mutableStateOf(FilterTarget.REMOTE) }
    var sparklesEnabled by remember { mutableStateOf(true) }
    var toddlerLockEnabled by remember { mutableStateOf(false) }
    var showKidsSheet by remember { mutableStateOf(false) }
    var isPeekABooOpen by remember { mutableStateOf(true) }

    val particleManager = remember { ParticleManager() }
    val faceTracker = remember { FaceTracker() }
    val detectedFace by faceTracker.detectedFace.collectAsState()

    val bubbleManager = remember {
        BubbleGameManager { id, _, _ ->
            val msg = JSONObject().apply {
                put("type", "BUBBLE_POP")
                put("id", id)
            }.toString()
            rtcClient.sendDataMessage(null, msg)
        }
    }

    val doodleManager = remember {
        DoodleManager(
            onStrokeFinished = { stroke ->
                val pointsArray = JSONArray()
                stroke.points.forEach { p ->
                    pointsArray.put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                    })
                }
                val msg = JSONObject().apply {
                    put("type", "DOODLE_STROKE")
                    put("color", stroke.color)
                    put("strokeWidth", stroke.strokeWidth.toDouble())
                    put("points", pointsArray)
                }.toString()
                rtcClient.sendDataMessage(null, msg)
            },
            onCleared = {
                val msg = JSONObject().apply {
                    put("type", "CLEAR_DOODLE")
                }.toString()
                rtcClient.sendDataMessage(null, msg)
            }
        )
    }

    // Pinning state: ID of pinned peer (or "local")
    var pinnedId by remember { mutableStateOf<String?>(null) }

    fun getDisplayName(uid: String): String {
        if (uid == currentUid || uid == "local") return "You"
        val found = contacts.find { it.registeredUser?.uid == uid || it.normalizedPhoneNumber == uid }
        return found?.displayName ?: "User ${uid.takeLast(4)}"
    }

    fun leaveCallInternal() {
        if (isEnded) return
        isEnded = true
        coroutineScope.launch {
            signalingRepo.leaveCall(callId, currentUid, groupId)
            CallService.stop(context)
            faceTracker.release()
            rtcClient.close()
            onCallEnded()
        }
    }

    BackHandler {
        if (toddlerLockEnabled) {
            Toast.makeText(context, "Toddler Lock is ON 🔒 Tap 🧸 to unlock", Toast.LENGTH_SHORT).show()
        } else {
            leaveCallInternal()
        }
    }

    LaunchedEffect(callSession?.status, callSession?.activeParticipants) {
        val session = callSession ?: return@LaunchedEffect
        val remaining = session.activeParticipants

        // Case 1: Status explicitly marked ENDED
        if (session.status == CallStatus.ENDED) {
            if (!isEnded) {
                isEnded = true
                CallService.stop(context)
                rtcClient.close()
                onCallEnded()
            }
            return@LaunchedEffect
        }

        // Case 2: Multi-party call where other participants left, leaving only 1 person
        if (session.maxParticipantsReached >= 2 && remaining.size <= 1 && remaining.contains(currentUid)) {
            leaveCallInternal()
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

        // Listen for DataChannel messages (interactivity & games)
        rtcClient.onDataMessageReceived = { _, message ->
            try {
                val json = JSONObject(message)
                when (json.optString("type")) {
                    "BUBBLE_POP" -> {
                        val id = json.getInt("id")
                        bubbleManager.popRemoteBubble(id, 1080f, 1920f)
                    }
                    "PEEK_A_BOO" -> {
                        isPeekABooOpen = json.getBoolean("isOpen")
                    }
                    "DOODLE_STROKE" -> {
                        val color = json.getLong("color")
                        val strokeWidth = json.getDouble("strokeWidth").toFloat()
                        val pointsArray = json.getJSONArray("points")
                        val points = (0 until pointsArray.length()).map { i ->
                            val pObj = pointsArray.getJSONObject(i)
                            DoodlePoint(pObj.getDouble("x").toFloat(), pObj.getDouble("y").toFloat())
                        }
                        doodleManager.addRemoteStroke(DoodleStroke(color, strokeWidth, points))
                    }
                    "CLEAR_DOODLE" -> {
                        doodleManager.strokes.clear()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupCallScreen", "Error parsing data message", e)
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
    // Deterministic tie-breaker: smaller UID creates offer to larger UID.
    LaunchedEffect(participants) {
        val otherPeers = participants.filter { it != currentUid }
        for (peerUid in otherPeers) {
            if (currentUid < peerUid) {
                rtcClient.createOffer(peerUid) { offer ->
                    coroutineScope.launch {
                        signalingRepo.sendOffer(callId, currentUid, peerUid, offer)
                    }
                }
            }
        }
        // Cleanup departed peers WebRTC tracks & connections
        val departedPeers = remoteVideoTracks.keys.filter { it !in participants }
        for (departed in departedPeers) {
            rtcClient.removePeer(departed)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceTracker.release()
            if (!isEnded) {
                isEnded = true
                coroutineScope.launch {
                    signalingRepo.leaveCall(callId, currentUid, groupId)
                }
                CallService.stop(context)
                rtcClient.close()
            }
        }
    }

    LaunchedEffect(currentFilter, filterTarget, localVideoTrack, remoteVideoTracks, pinnedId) {
        if (currentFilter == InteractiveFilterType.NONE) {
            faceTracker.detach()
        } else {
            if (filterTarget == FilterTarget.LOCAL) {
                faceTracker.attach(localVideoTrack, mirror = true)
            } else {
                val targetTrack = if (pinnedId != null && pinnedId != "local" && pinnedId != currentUid) {
                    remoteVideoTracks[pinnedId]
                } else {
                    remoteVideoTracks.values.firstOrNull()
                }
                faceTracker.attach(targetTrack, mirror = false)
            }
        }
    }

    val otherPeers = (participants.filter { it != currentUid } + remoteVideoTracks.keys).distinct()

    val bottomSpacerHeight by animateDpAsState(
        targetValue = if (areControlsVisible) 100.dp else 16.dp,
        animationSpec = tween(300),
        label = "bottomSpacer"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar Header
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
                exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
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
                            text = "${otherPeers.size + 1} in call · Tap participant to pin",
                            style = MaterialTheme.typography.bodySmall,
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
            }

            // Dynamic Vertical-First Video Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    // 1. PINNED MODE: Main stage + bottom carousel of thumbnails
                    pinnedId != null -> {
                        val activePinnedId = pinnedId!!
                        val isPinnedLocal = activePinnedId == "local" || activePinnedId == currentUid
                        val pinnedTrack = if (isPinnedLocal) {
                            if (isVideo && isVideoEnabled) localVideoTrack else null
                        } else {
                            remoteVideoTracks[activePinnedId]
                        }

                        val remainingPeers = if (isPinnedLocal) {
                            otherPeers
                        } else {
                            listOf("local") + otherPeers.filter { it != activePinnedId }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Main Pinned Stage (Full width vertical viewport)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                ParticipantTile(
                                    title = getDisplayName(activePinnedId),
                                    videoTrack = pinnedTrack,
                                    eglBase = rtcClient.eglBase,
                                    isMirror = isPinnedLocal,
                                    isMuted = if (isPinnedLocal) isMicMuted else false,
                                    isPinned = true,
                                    isOverlay = false,
                                    onPinClick = {
                                        resetControlsTimer()
                                        pinnedId = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Bottom Strip of Other Participants (Vertical thumbnails)
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(remainingPeers, key = { it }) { peerId ->
                                    val isPeerLocal = peerId == "local" || peerId == currentUid
                                    val track = if (isPeerLocal) {
                                        if (isVideo && isVideoEnabled) localVideoTrack else null
                                    } else {
                                        remoteVideoTracks[peerId]
                                    }
                                    ParticipantTile(
                                        title = getDisplayName(peerId),
                                        videoTrack = track,
                                        eglBase = rtcClient.eglBase,
                                        isMirror = isPeerLocal,
                                        isMuted = if (isPeerLocal) isMicMuted else false,
                                        isPinned = false,
                                        isOverlay = true,
                                        onPinClick = {
                                            resetControlsTimer()
                                            pinnedId = peerId
                                        },
                                        modifier = Modifier
                                            .size(width = 105.dp, height = 150.dp)
                                            .clickable {
                                                resetControlsTimer()
                                                pinnedId = peerId
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // 2. ONE-ON-ONE IN GROUP (Single remote peer + Local PIP)
                    otherPeers.size == 1 -> {
                        val singlePeer = otherPeers.first()
                        val remoteTrack = remoteVideoTracks[singlePeer]

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Fullscreen Vertical Remote Participant
                            ParticipantTile(
                                title = getDisplayName(singlePeer),
                                videoTrack = remoteTrack,
                                eglBase = rtcClient.eglBase,
                                isMirror = false,
                                isMuted = false,
                                isPinned = false,
                                isOverlay = false,
                                onPinClick = {
                                    resetControlsTimer()
                                    pinnedId = singlePeer
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )

                            // Local PIP Card in Top Right
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 24.dp)
                                    .size(width = 110.dp, height = 160.dp)
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        resetControlsTimer()
                                        pinnedId = "local"
                                    },
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                ParticipantTile(
                                    title = "You",
                                    videoTrack = if (isVideo && isVideoEnabled) localVideoTrack else null,
                                    eglBase = rtcClient.eglBase,
                                    isMirror = true,
                                    isMuted = isMicMuted,
                                    isPinned = false,
                                    isOverlay = true,
                                    onPinClick = {
                                        resetControlsTimer()
                                        pinnedId = "local"
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 3. TWO REMOTE PEERS: Split-screen vertical halves + Local PIP
                    otherPeers.size == 2 -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                otherPeers.forEach { peerId ->
                                    ParticipantTile(
                                        title = getDisplayName(peerId),
                                        videoTrack = remoteVideoTracks[peerId],
                                        eglBase = rtcClient.eglBase,
                                        isMirror = false,
                                        isMuted = false,
                                        isPinned = false,
                                        isOverlay = false,
                                        onPinClick = {
                                            resetControlsTimer()
                                            pinnedId = peerId
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            }

                            // Local PIP in top right
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 24.dp)
                                    .size(width = 100.dp, height = 145.dp)
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        resetControlsTimer()
                                        pinnedId = "local"
                                    },
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                ParticipantTile(
                                    title = "You",
                                    videoTrack = if (isVideo && isVideoEnabled) localVideoTrack else null,
                                    eglBase = rtcClient.eglBase,
                                    isMirror = true,
                                    isMuted = isMicMuted,
                                    isPinned = false,
                                    isOverlay = true,
                                    onPinClick = {
                                        resetControlsTimer()
                                        pinnedId = "local"
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // 4. THREE OR MORE PEERS: Vertical grid with portrait tiles (3:4 ratio)
                    else -> {
                        val allTiles = listOf("local") + otherPeers
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allTiles, key = { it }) { id ->
                                val isPeerLocal = id == "local" || id == currentUid
                                val track = if (isPeerLocal) {
                                    if (isVideo && isVideoEnabled) localVideoTrack else null
                                } else {
                                    remoteVideoTracks[id]
                                }
                                ParticipantTile(
                                    title = getDisplayName(id),
                                    videoTrack = track,
                                    eglBase = rtcClient.eglBase,
                                    isMirror = isPeerLocal,
                                    isMuted = if (isPeerLocal) isMicMuted else false,
                                    isPinned = false,
                                    isOverlay = true,
                                    onPinClick = {
                                        resetControlsTimer()
                                        pinnedId = id
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clickable {
                                            resetControlsTimer()
                                            pinnedId = id
                                        }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(bottomSpacerHeight))
        }

        // Interactive Face Filters Overlay
        FaceFiltersOverlay(
            filterType = currentFilter,
            face = detectedFace
        )

        // Interactive Games Overlays
        when (currentGame) {
            InteractiveGameType.BUBBLE_POP -> {
                BubblePopOverlay(
                    manager = bubbleManager,
                    particleManager = particleManager
                )
            }
            InteractiveGameType.PEEK_A_BOO -> {
                PeekABooOverlay(
                    isOpen = isPeekABooOpen,
                    onToggle = { nextState ->
                        isPeekABooOpen = nextState
                        val msg = JSONObject().apply {
                            put("type", "PEEK_A_BOO")
                            put("isOpen", nextState)
                        }.toString()
                        rtcClient.sendDataMessage(null, msg)
                    },
                    particleManager = particleManager
                )
            }
            InteractiveGameType.DOODLE -> {
                DoodleOverlay(manager = doodleManager)
            }
            InteractiveGameType.NONE -> {}
        }

        // Touch Sparkles Particle Overlay
        ParticleOverlay(
            particleManager = particleManager,
            enabled = sparklesEnabled
        )

        // Bottom Call Controls Bar
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
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
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

                    if (isVideo) {
                        // Camera Flip
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
                            Icon(Icons.Rounded.Cameraswitch, contentDescription = "Flip")
                        }

                        // Video Camera On/Off
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
                                imageVector = if (!isVideoEnabled) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                                contentDescription = "Video"
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
                            containerColor = if (isSpeakerOn) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (isSpeakerOn) Color(0xFF38BDF8) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown,
                            contentDescription = "Speaker"
                        )
                    }

                    // Kids Fun Zone Button (🧸 / 🎈)
                    FilledIconButton(
                        onClick = {
                            resetControlsTimer()
                            showKidsSheet = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (currentFilter != InteractiveFilterType.NONE || currentGame != InteractiveGameType.NONE)
                                Color(0xFFEC4899).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (currentFilter != InteractiveFilterType.NONE || currentGame != InteractiveGameType.NONE)
                                Color(0xFFF472B6) else Color.White
                        )
                    ) {
                        Text(text = "🧸", fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // End Call Button
                    FilledIconButton(
                        onClick = {
                            resetControlsTimer()
                            if (toddlerLockEnabled) {
                                Toast.makeText(context, "Toddler Lock is ON 🔒 Tap 🧸 to unlock", Toast.LENGTH_SHORT).show()
                            } else {
                                leaveCallInternal()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RoseCallDecline,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Kids Fun Zone Bottom Sheet
        if (showKidsSheet) {
            KidsModeBottomSheet(
                onDismissRequest = { showKidsSheet = false },
                currentFilter = currentFilter,
                onFilterSelected = { currentFilter = it },
                currentGame = currentGame,
                onGameSelected = { currentGame = it },
                target = filterTarget,
                onTargetChanged = { filterTarget = it },
                sparklesEnabled = sparklesEnabled,
                onSparklesToggled = { sparklesEnabled = it },
                toddlerLockEnabled = toddlerLockEnabled,
                onToddlerLockToggled = { toddlerLockEnabled = it }
            )
        }
    }
}

@Composable
fun ParticipantTile(
    title: String,
    videoTrack: VideoTrack?,
    eglBase: org.webrtc.EglBase,
    isMirror: Boolean = false,
    isMuted: Boolean = false,
    isPinned: Boolean = false,
    isOverlay: Boolean = false,
    onPinClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (videoTrack != null) {
                WebRtcSurfaceView(
                    modifier = Modifier.fillMaxSize(),
                    videoTrack = videoTrack,
                    eglBase = eglBase,
                    isMirror = isMirror,
                    isOverlay = isOverlay
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
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

            // Top-right Pin Indicator & Toggle Button
            if (onPinClick != null) {
                IconButton(
                    onClick = onPinClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.PinDrop,
                        contentDescription = if (isPinned) "Unpin" else "Pin",
                        tint = if (isPinned) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Bottom-left name and mic tag
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
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
