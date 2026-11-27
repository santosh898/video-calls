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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.videocalls.app.ui.interactive.*
import com.videocalls.app.ui.theme.RoseCallDecline
import com.videocalls.app.webrtc.WebRtcClient
import com.videocalls.app.webrtc.WebRtcSurfaceView
import kotlinx.coroutines.delay

@Composable
fun SecretFeaturesTestScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Check Camera & Audio Permissions
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    if (!hasCameraPermission || !hasAudioPermission) {
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
                Text(
                    text = "🧪 Feature Testing Lab",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera and microphone permissions are required to test in-call effects and games.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    permissionsLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                }) {
                    Text("Grant Permissions")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onBack) {
                    Text("Exit Lab", color = Color(0xFF94A3B8))
                }
            }
        }
        return
    }

    // WebRTC Client for camera preview
    val rtcClient = remember { WebRtcClient(context) }
    val localVideoTrack by rtcClient.localVideoTrackFlow.collectAsState()
    val isMicMuted by rtcClient.isMicMuted.collectAsState()
    val isVideoEnabled by rtcClient.isVideoEnabled.collectAsState()

    // Interactive & Kids Features State
    var currentFilter by remember { mutableStateOf(InteractiveEffectType.NONE) }
    var currentBackground by remember { mutableStateOf(InteractiveBackgroundType.NONE) }
    var currentGame by remember { mutableStateOf(InteractiveGameType.NONE) }
    var sparklesEnabled by remember { mutableStateOf(false) }
    var showKidsSheet by remember { mutableStateOf(false) }
    var isPeekABooOpen by remember { mutableStateOf(true) }
    var hasUserManuallySelectedEffect by remember { mutableStateOf(false) }

    var isKidsFeaturesActive by remember { mutableStateOf(false) }
    var badgeToastMessage by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(badgeToastMessage) {
        if (badgeToastMessage != null) {
            delay(1600L)
            badgeToastMessage = null
        }
    }

    // Initialize local camera capturer
    LaunchedEffect(Unit) {
        rtcClient.startLocalMedia(isAudioOnly = false)
    }

    // Trackers for AR Face Filter and Background Segmentation
    val particleManager = remember { ParticleManager() }
    val faceTracker = remember { FaceTracker() }
    val detectedFace by faceTracker.detectedFace.collectAsState()

    val segTracker = remember { SelfieSegmentationTracker() }
    val maskBitmap by segTracker.maskBitmap.collectAsState()
    var cameraFrameBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val lettersManager = remember {
        FlyingLettersManager { _, _, _, _, _ -> }
    }
    val bubbleManager = remember {
        BubbleGameManager { _, _, _ -> }
    }

    LaunchedEffect(currentFilter, localVideoTrack) {
        if (currentFilter == InteractiveEffectType.NONE) {
            faceTracker.detach()
        } else {
            faceTracker.attach(localVideoTrack, mirror = true)
        }
    }

    LaunchedEffect(currentBackground, localVideoTrack) {
        if (currentBackground == InteractiveBackgroundType.NONE) {
            segTracker.detach()
        } else {
            segTracker.attach(localVideoTrack, mirror = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            faceTracker.release()
            segTracker.release()
            rtcClient.close()
        }
    }

    BackHandler(enabled = true) {
        if (isKidsFeaturesActive) {
            Toast.makeText(context, "Kids Mode is ON 🔒 Tap 🧸 or Exit to leave", Toast.LENGTH_SHORT).show()
            areControlsVisible = true
            resetControlsTimer()
        } else {
            onBack()
        }
    }

    fun cycleNextBackground(forward: Boolean = true) {
        val allBackgrounds = InteractiveBackgroundType.values()
        val curIndex = allBackgrounds.indexOf(currentBackground)
        val nextIndex = if (forward) {
            if (curIndex == -1) 0 else (curIndex + 1) % allBackgrounds.size
        } else {
            if (curIndex <= 0) allBackgrounds.size - 1 else curIndex - 1
        }
        val nextBg = allBackgrounds[nextIndex]
        currentBackground = nextBg
        areControlsVisible = false
        if (nextBg != InteractiveBackgroundType.NONE) {
            isKidsFeaturesActive = true
            KidsSoundManager.playWhoosh()
            badgeToastMessage = "${nextBg.icon} ${nextBg.displayName}"
        } else {
            badgeToastMessage = "🚫 No Scenery"
        }
    }

    fun cycleNextEffect() {
        if (!hasUserManuallySelectedEffect) return
        val allEffects = InteractiveEffectType.values()
        val curIndex = allEffects.indexOf(currentFilter)
        val nextIndex = if (curIndex == -1) 0 else (curIndex + 1) % allEffects.size
        val nextEffect = allEffects[nextIndex]
        currentFilter = nextEffect
        areControlsVisible = false
        if (nextEffect != InteractiveEffectType.NONE) {
            isKidsFeaturesActive = true
            KidsSoundManager.playSparkle()
            badgeToastMessage = "${nextEffect.icon} ${nextEffect.displayName}"
        } else {
            badgeToastMessage = "🚫 No Filter"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
        // 1. Live Camera Surface View
        if (isVideoEnabled && localVideoTrack != null) {
            WebRtcSurfaceView(
                modifier = Modifier.fillMaxSize(),
                videoTrack = localVideoTrack,
                eglBase = rtcClient.eglBase,
                isMirror = true,
                isOverlay = false,
                onClick = {
                    areControlsVisible = !areControlsVisible
                    if (areControlsVisible) {
                        resetControlsTimer()
                    }
                },
                captureFrames = currentFilter == InteractiveEffectType.KALEIDOSCOPE,
                onFrameCaptured = { bmp ->
                    cameraFrameBitmap = bmp
                }
            )

            // Cartoon Background Scenery Overlay
            CartoonBackgroundOverlay(
                backgroundType = currentBackground,
                maskBitmap = maskBitmap
            )

            // AR Face Filters Overlay
            FaceFiltersOverlay(
                filterType = currentFilter,
                face = detectedFace,
                cameraFrame = cameraFrameBitmap
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.VideocamOff,
                        contentDescription = "Camera Off",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera is Turned Off",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Gesture Detection Overlay on Video ONLY when user manually selected an effect/filter from menu
        if (currentGame == InteractiveGameType.NONE && isKidsFeaturesActive && hasUserManuallySelectedEffect) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var isTap = true
                            var totalDx = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    val dx = change.position.x - startX
                                    val dy = change.position.y - startY
                                    if (kotlin.math.abs(dx) > 25 || kotlin.math.abs(dy) > 25) {
                                        isTap = false
                                    }
                                    totalDx = dx
                                } else {
                                    if (isTap) {
                                        cycleNextEffect()
                                    } else if (kotlin.math.abs(totalDx) > 80) {
                                        cycleNextBackground(forward = totalDx > 0)
                                    }
                                    break
                                }
                            }
                        }
                    }
            )
        }

        // Animated Badge Notification Pill
        AnimatedVisibility(
            visible = badgeToastMessage != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
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
            InteractiveGameType.ALPHABET -> {
                FlyingLettersOverlay(
                    gameType = InteractiveGameType.ALPHABET,
                    manager = lettersManager,
                    particleManager = particleManager
                )
            }
            InteractiveGameType.NUMBERS -> {
                FlyingLettersOverlay(
                    gameType = InteractiveGameType.NUMBERS,
                    manager = lettersManager,
                    particleManager = particleManager
                )
            }
            InteractiveGameType.BUBBLE_POP -> {
                BubblePopOverlay(
                    manager = bubbleManager,
                    particleManager = particleManager,
                    onTap = null
                )
            }
            InteractiveGameType.PEEK_A_BOO -> {
                PeekABooOverlay(
                    isOpen = isPeekABooOpen,
                    onToggle = { isPeekABooOpen = it },
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
            }
        )

        // Persistent Translucent Menu Pill in Corner: ALWAYS present when controls are hidden
        AnimatedVisibility(
            visible = !areControlsVisible,
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Audio Toggle
                    IconButton(
                        onClick = {
                            rtcClient.toggleMic()
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (isMicMuted) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = if (isMicMuted) "Unmute" else "Mute",
                            tint = if (isMicMuted) Color(0xFFF87171) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Flip Camera
                    IconButton(
                        onClick = {
                            rtcClient.flipCamera()
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Toggle Video
                    IconButton(
                        onClick = {
                            rtcClient.toggleVideo()
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (!isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                            contentDescription = if (isVideoEnabled) "Turn off camera" else "Turn on camera",
                            tint = if (!isVideoEnabled) Color(0xFFF87171) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Kids Fun Zone Button (🧸)
                    IconButton(
                        onClick = {
                            showKidsSheet = true
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (isKidsFeaturesActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Text(text = "🧸", fontSize = 24.sp)
                    }

                    // Exit Lab Button (Red Call End style)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(56.dp)
                            .background(RoseCallDecline, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Exit Lab",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        // Kids Features Bottom Sheet
        if (showKidsSheet) {
            KidsModeBottomSheet(
                onDismissRequest = { showKidsSheet = false },
                currentBackground = currentBackground,
                onBackgroundSelected = {
                    currentBackground = it
                    hasUserManuallySelectedEffect = true
                    if (it != InteractiveBackgroundType.NONE) {
                        isKidsFeaturesActive = true
                    }
                    showKidsSheet = false
                    areControlsVisible = false
                },
                currentEffect = currentFilter,
                onEffectSelected = {
                    currentFilter = it
                    hasUserManuallySelectedEffect = true
                    if (it != InteractiveEffectType.NONE) {
                        isKidsFeaturesActive = true
                    }
                    showKidsSheet = false
                    areControlsVisible = false
                },
                currentGame = currentGame,
                onGameSelected = {
                    currentGame = it
                    lettersManager.resetIndices()
                    if (it != InteractiveGameType.NONE) {
                        isKidsFeaturesActive = true
                    }
                    showKidsSheet = false
                    areControlsVisible = false
                },
                sparklesEnabled = sparklesEnabled,
                onSparklesToggled = { sparklesEnabled = it },
                isKidsFeaturesActive = isKidsFeaturesActive,
                onKidsFeaturesToggled = { enabled ->
                    isKidsFeaturesActive = enabled
                    if (!enabled) {
                        currentFilter = InteractiveEffectType.NONE
                        currentBackground = InteractiveBackgroundType.NONE
                        currentGame = InteractiveGameType.NONE
                        areControlsVisible = true
                        showKidsSheet = false
                        Toast.makeText(context, "Kids Mode turned OFF", Toast.LENGTH_SHORT).show()
                    } else {
                        showKidsSheet = false
                        areControlsVisible = false
                        Toast.makeText(context, "Kids Mode turned ON", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
