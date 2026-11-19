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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.layout.onSizeChanged
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

    // Interactive & Toddler Features (Inactive by default)
    var currentFilter by remember { mutableStateOf(InteractiveEffectType.NONE) }
    var currentBackground by remember { mutableStateOf(InteractiveBackgroundType.NONE) }
    var currentGame by remember { mutableStateOf(InteractiveGameType.NONE) }
    var sparklesEnabled by remember { mutableStateOf(false) }
    var toddlerLockEnabled by remember { mutableStateOf(false) }
    var showKidsSheet by remember { mutableStateOf(false) }
    var isPeekABooOpen by remember { mutableStateOf(true) }

    // Internal tracker for when Kids features are active
    // Activates when at least one filter/effect or background is set, deactivates ONLY when both are set to NONE via menu
    var isKidsFeaturesActive by remember { mutableStateOf(false) }

    // Mirrored Filter & Background Applied by Remote Caller on Me
    var remoteAppliedFilterOnMe by remember { mutableStateOf(InteractiveEffectType.NONE) }
    var remoteAppliedBackground by remember { mutableStateOf(InteractiveBackgroundType.NONE) }
    var contextNotificationMessage by remember { mutableStateOf<String?>(null) }
    var badgeToastMessage by remember { mutableStateOf<String?>(null) }

    var screenWidth by remember { mutableFloatStateOf(1080f) }
    var screenHeight by remember { mutableFloatStateOf(1920f) }

    LaunchedEffect(contextNotificationMessage) {
        if (contextNotificationMessage != null) {
            delay(3500L)
            contextNotificationMessage = null
        }
    }

    LaunchedEffect(badgeToastMessage) {
        if (badgeToastMessage != null) {
            delay(1600L)
            badgeToastMessage = null
        }
    }

    val particleManager = remember { ParticleManager() }
    val faceTracker = remember { FaceTracker() }
    val detectedFace by faceTracker.detectedFace.collectAsState()

    val pipFaceTracker = remember { FaceTracker() }
    val pipDetectedFace by pipFaceTracker.detectedFace.collectAsState()

    val mainSegTracker = remember { SelfieSegmentationTracker() }
    val mainMaskBitmap by mainSegTracker.maskBitmap.collectAsState()

    val pipSegTracker = remember { SelfieSegmentationTracker() }
    val pipMaskBitmap by pipSegTracker.maskBitmap.collectAsState()

    fun sendFeedFilter(filter: InteractiveEffectType) {
        val msg = JSONObject().apply {
            put("type", "SET_FEED_FILTER")
            put("filter", filter.name)
        }.toString()
        rtcClient.sendDataMessage(null, msg)
    }

    fun sendBackgroundSync(bg: InteractiveBackgroundType) {
        val msg = JSONObject().apply {
            put("type", "SET_BACKGROUND")
            put("background", bg.name)
        }.toString()
        rtcClient.sendDataMessage(null, msg)
    }

    fun sendGameSync(game: InteractiveGameType) {
        val msg = JSONObject().apply {
            put("type", "GAME_SYNC")
            put("game", game.name)
        }.toString()
        rtcClient.sendDataMessage(null, msg)
    }

    fun cycleNextBackground(forward: Boolean = true) {
        val cyclingBackgrounds = InteractiveBackgroundType.values().filter { it != InteractiveBackgroundType.NONE }
        val curIndex = cyclingBackgrounds.indexOf(currentBackground)
        val nextIndex = if (forward) {
            if (curIndex == -1) 0 else (curIndex + 1) % cyclingBackgrounds.size
        } else {
            if (curIndex <= 0) cyclingBackgrounds.size - 1 else curIndex - 1
        }
        val nextBg = cyclingBackgrounds[nextIndex]
        currentBackground = nextBg
        sendBackgroundSync(nextBg)
        isKidsFeaturesActive = true
        KidsSoundManager.playWhoosh()
        badgeToastMessage = "${nextBg.icon} ${nextBg.displayName}"
    }

    fun cycleNextEffect() {
        val cyclingEffects = InteractiveEffectType.values().filter { it != InteractiveEffectType.NONE }
        val curIndex = cyclingEffects.indexOf(currentFilter)
        val nextIndex = if (curIndex == -1) 0 else (curIndex + 1) % cyclingEffects.size
        val nextEffect = cyclingEffects[nextIndex]
        currentFilter = nextEffect
        sendFeedFilter(nextEffect)
        isKidsFeaturesActive = true
        KidsSoundManager.playSparkle()
        badgeToastMessage = "${nextEffect.icon} ${nextEffect.displayName}"
    }

    val bubbleManager = remember {
        BubbleGameManager { id, _, _ ->
            val msg = JSONObject().apply {
                put("type", "BUBBLE_POP")
                put("id", id)
            }.toString()
            rtcClient.sendDataMessage(null, msg)
        }
    }

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
            faceTracker.release()
            pipFaceTracker.release()
            mainSegTracker.release()
            pipSegTracker.release()
            rtcClient.close()
            onCallEnded()
        }
    }

    BackHandler {
        if (toddlerLockEnabled) {
            Toast.makeText(context, "Toddler Lock is ON 🔒 Tap 🧸 to unlock", Toast.LENGTH_SHORT).show()
        } else {
            endCallInternal()
        }
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

        // Listen for DataChannel messages (interactivity & games)
        rtcClient.onDataMessageReceived = { _, message ->
            try {
                val json = JSONObject(message)
                when (json.optString("type")) {
                    "SET_FEED_FILTER", "FILTER_SYNC" -> {
                        val filterName = json.optString("filter")
                        val filter = try {
                            InteractiveFilterType.valueOf(filterName)
                        } catch (_: Exception) {
                            InteractiveFilterType.NONE
                        }
                        remoteAppliedFilterOnMe = filter
                        if (filter != InteractiveFilterType.NONE) {
                            isKidsFeaturesActive = true
                            contextNotificationMessage = "${filter.icon} $targetName gave you ${filter.displayName} filter!"
                            KidsSoundManager.playSparkle()
                        } else {
                            if (currentFilter == InteractiveEffectType.NONE &&
                                currentBackground == InteractiveBackgroundType.NONE &&
                                remoteAppliedBackground == InteractiveBackgroundType.NONE &&
                                currentGame == InteractiveGameType.NONE
                            ) {
                                isKidsFeaturesActive = false
                            }
                        }
                    }
                    "GAME_SYNC" -> {
                        val gameName = json.optString("game")
                        val game = try {
                            InteractiveGameType.valueOf(gameName)
                        } catch (_: Exception) {
                            InteractiveGameType.NONE
                        }
                        currentGame = game
                        if (game != InteractiveGameType.NONE) {
                            isKidsFeaturesActive = true
                        } else if (currentFilter == InteractiveEffectType.NONE &&
                            remoteAppliedFilterOnMe == InteractiveEffectType.NONE &&
                            currentBackground == InteractiveBackgroundType.NONE &&
                            remoteAppliedBackground == InteractiveBackgroundType.NONE
                        ) {
                            isKidsFeaturesActive = false
                        }
                    }
                    "SPARKLE_BURST" -> {
                        val x = json.optDouble("x", 0.5).toFloat()
                        val y = json.optDouble("y", 0.5).toFloat()
                        particleManager.burst(x * screenWidth, y * screenHeight, 18)
                        KidsSoundManager.playSparkle()
                    }
                    "BUBBLE_POP" -> {
                        val id = json.getInt("id")
                        bubbleManager.popRemoteBubble(id, screenWidth, screenHeight)
                    }
                    "PEEK_A_BOO" -> {
                        isPeekABooOpen = json.getBoolean("isOpen")
                    }
                    "SET_BACKGROUND" -> {
                        val bgName = json.optString("background")
                        val bg = try {
                            InteractiveBackgroundType.valueOf(bgName)
                        } catch (_: Exception) {
                            InteractiveBackgroundType.NONE
                        }
                        remoteAppliedBackground = bg
                        if (bg != InteractiveBackgroundType.NONE) {
                            isKidsFeaturesActive = true
                            contextNotificationMessage = "${bg.icon} $targetName set ${bg.displayName} scenery!"
                            KidsSoundManager.playWhoosh()
                        } else {
                            if (currentFilter == InteractiveEffectType.NONE &&
                                remoteAppliedFilterOnMe == InteractiveEffectType.NONE &&
                                currentBackground == InteractiveBackgroundType.NONE &&
                                currentGame == InteractiveGameType.NONE
                            ) {
                                isKidsFeaturesActive = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SingleCallScreen", "Error parsing data message", e)
            }
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
            faceTracker.release()
            pipFaceTracker.release()
            mainSegTracker.release()
            pipSegTracker.release()
            if (!isEnded) {
                isEnded = true
                CallService.stop(context)
                rtcClient.close()
            }
        }
    }

    val remoteTrack = remoteVideoTracks.values.firstOrNull()

    val activeMainFilter = currentFilter
    val activePipFilter = remoteAppliedFilterOnMe

    val activeMainBackground = if (currentBackground != InteractiveBackgroundType.NONE) currentBackground else remoteAppliedBackground
    val activePipBackground = remoteAppliedBackground

    val isKidsActive = isKidsFeaturesActive ||
            currentFilter != InteractiveEffectType.NONE ||
            remoteAppliedFilterOnMe != InteractiveEffectType.NONE ||
            currentBackground != InteractiveBackgroundType.NONE ||
            remoteAppliedBackground != InteractiveBackgroundType.NONE ||
            currentGame != InteractiveGameType.NONE ||
            sparklesEnabled ||
            toddlerLockEnabled

    LaunchedEffect(activeMainFilter, remoteTrack) {
        if (activeMainFilter == InteractiveFilterType.NONE) {
            faceTracker.detach()
        } else {
            faceTracker.attach(remoteTrack, mirror = false)
        }
    }

    LaunchedEffect(activePipFilter, localVideoTrack) {
        if (activePipFilter == InteractiveFilterType.NONE) {
            pipFaceTracker.detach()
        } else {
            pipFaceTracker.attach(localVideoTrack, mirror = true)
        }
    }

    LaunchedEffect(activeMainBackground, remoteTrack) {
        if (activeMainBackground == InteractiveBackgroundType.NONE) {
            mainSegTracker.detach()
        } else {
            mainSegTracker.attach(remoteTrack, mirror = false)
        }
    }

    LaunchedEffect(activePipBackground, localVideoTrack) {
        if (activePipBackground == InteractiveBackgroundType.NONE) {
            pipSegTracker.detach()
        } else {
            pipSegTracker.attach(localVideoTrack, mirror = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    screenWidth = size.width.toFloat()
                    screenHeight = size.height.toFloat()
                }
            }
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
                isOverlay = false,
                onClick = {
                    areControlsVisible = !areControlsVisible
                    if (areControlsVisible) {
                        resetControlsTimer()
                    }
                }
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

        // Context Notification Banner when peer applies filter on this device
        AnimatedVisibility(
            visible = contextNotificationMessage != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0284C7).copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = contextNotificationMessage ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
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
                Box(modifier = Modifier.fillMaxSize()) {
                    WebRtcSurfaceView(
                        modifier = Modifier.fillMaxSize(),
                        videoTrack = localVideoTrack,
                        eglBase = rtcClient.eglBase,
                        isMirror = true,
                        isOverlay = true
                    )
                    CartoonBackgroundOverlay(
                        backgroundType = activePipBackground,
                        maskBitmap = pipMaskBitmap
                    )
                    FaceFiltersOverlay(
                        filterType = activePipFilter,
                        face = pipDetectedFace
                    )
                }
            }
        }

        // Cartoon Background Scenery Overlay on Main Video (Replacing background like GMeet / Teams)
        CartoonBackgroundOverlay(
            backgroundType = activeMainBackground,
            maskBitmap = mainMaskBitmap
        )

        // Interactive Face Filters / Effects Overlay on Main Video
        FaceFiltersOverlay(
            filterType = activeMainFilter,
            face = detectedFace
        )

        // Gesture Detection Overlay on Video ONLY when kids features are active
        if (isVideo && currentGame == InteractiveGameType.NONE && isKidsFeaturesActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isDrag = false
                            val slop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDrag) {
                                        cycleNextEffect()
                                    } else {
                                        if (kotlin.math.abs(totalDragX) > slop * 1.5f ||
                                            kotlin.math.abs(totalDragY) > slop * 1.5f
                                        ) {
                                            val forward = if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY)) {
                                                totalDragX < 0f // Swipe left -> forward
                                            } else {
                                                totalDragY < 0f // Swipe up -> forward
                                            }
                                            cycleNextBackground(forward)
                                        }
                                    }
                                    break
                                } else {
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDragX += dx
                                    totalDragY += dy
                                    if (kotlin.math.abs(totalDragX) > slop || kotlin.math.abs(totalDragY) > slop) {
                                        isDrag = true
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // Gesture Toast Badge ("🚀 Space Galaxy", "🕶️ Cool Shades")
        AnimatedVisibility(
            visible = badgeToastMessage != null,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = badgeToastMessage ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }

        // Interactive Games Overlays
        when (currentGame) {
            InteractiveGameType.BUBBLE_POP -> {
                BubblePopOverlay(
                    manager = bubbleManager,
                    particleManager = particleManager,
                    onTap = {
                        areControlsVisible = true
                        resetControlsTimer()
                    }
                )
            }
            InteractiveGameType.PEEK_A_BOO -> {
                PeekABooOverlay(
                    isOpen = isPeekABooOpen,
                    onToggle = { nextState ->
                        isPeekABooOpen = nextState
                        resetControlsTimer()
                        val msg = JSONObject().apply {
                            put("type", "PEEK_A_BOO")
                            put("isOpen", nextState)
                        }.toString()
                        rtcClient.sendDataMessage(null, msg)
                    },
                    particleManager = particleManager
                )
            }
            InteractiveGameType.NONE -> {}
        }

        // Touch Sparkles Particle Overlay
        ParticleOverlay(
            particleManager = particleManager,
            enabled = sparklesEnabled,
            onTap = {
                areControlsVisible = true
                resetControlsTimer()
            },
            onTapBurst = { normX, normY ->
                val msg = JSONObject().apply {
                    put("type", "SPARKLE_BURST")
                    put("x", normX.toDouble())
                    put("y", normY.toDouble())
                }.toString()
                rtcClient.sendDataMessage(null, msg)
            }
        )

        // Persistent Translucent Menu Pill in Corner: ONLY present when kids features are active AND controls are hidden
        AnimatedVisibility(
            visible = !areControlsVisible && isKidsFeaturesActive,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
        ) {
            Surface(
                onClick = {
                    areControlsVisible = true
                    resetControlsTimer()
                },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Show Controls",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Controls",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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

                    // Speakerphone Toggle (Audio calls only, since video calls default to speakerphone)
                    if (!isVideo) {
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
                    }

                    // Kids Fun Zone Button (🧸 / 🎈)
                    FilledIconButton(
                        onClick = {
                            resetControlsTimer()
                            showKidsSheet = true
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isKidsFeaturesActive)
                                Color(0xFFEC4899).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                            contentColor = if (isKidsFeaturesActive)
                                Color(0xFFF472B6) else Color.White
                        )
                    ) {
                        Text(text = "🧸", fontSize = 20.sp)
                    }

                    // End Call (Rose red circular button)
                    FilledIconButton(
                        onClick = {
                            if (toddlerLockEnabled) {
                                Toast.makeText(context, "Toddler Lock is ON 🔒 Tap 🧸 to unlock", Toast.LENGTH_SHORT).show()
                            } else {
                                endCallInternal()
                            }
                        },
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

        // Kids Fun Zone Bottom Sheet
        if (showKidsSheet) {
            KidsModeBottomSheet(
                onDismissRequest = { showKidsSheet = false },
                currentBackground = currentBackground,
                onBackgroundSelected = {
                    currentBackground = it
                    sendBackgroundSync(it)
                    if (it != InteractiveBackgroundType.NONE) {
                        isKidsFeaturesActive = true
                    } else if (currentFilter == InteractiveEffectType.NONE &&
                               remoteAppliedFilterOnMe == InteractiveEffectType.NONE &&
                               currentGame == InteractiveGameType.NONE
                    ) {
                        isKidsFeaturesActive = false
                    }
                },
                currentEffect = currentFilter,
                onEffectSelected = {
                    currentFilter = it
                    sendFeedFilter(it)
                    if (it != InteractiveEffectType.NONE) {
                        isKidsFeaturesActive = true
                    } else if (currentBackground == InteractiveBackgroundType.NONE &&
                               remoteAppliedBackground == InteractiveBackgroundType.NONE &&
                               currentGame == InteractiveGameType.NONE
                    ) {
                        isKidsFeaturesActive = false
                    }
                },
                currentGame = currentGame,
                onGameSelected = {
                    currentGame = it
                    sendGameSync(it)
                    if (it != InteractiveGameType.NONE) {
                        isKidsFeaturesActive = true
                    } else if (currentBackground == InteractiveBackgroundType.NONE &&
                               remoteAppliedBackground == InteractiveBackgroundType.NONE &&
                               currentFilter == InteractiveEffectType.NONE &&
                               remoteAppliedFilterOnMe == InteractiveEffectType.NONE
                    ) {
                        isKidsFeaturesActive = false
                    }
                },
                sparklesEnabled = sparklesEnabled,
                onSparklesToggled = { sparklesEnabled = it },
                toddlerLockEnabled = toddlerLockEnabled,
                onToddlerLockToggled = { toddlerLockEnabled = it }
            )
        }
    }
}
