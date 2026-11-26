package com.videocalls.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.videocalls.app.data.model.CallStatus
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.service.CallNotificationHelper
import com.videocalls.app.ui.navigation.AppNavGraph
import com.videocalls.app.ui.navigation.Screen
import com.videocalls.app.ui.theme.VideoCallsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val authRepository by lazy { com.videocalls.app.data.repository.AuthRepository(this) }
    private val signalingRepo by lazy { CallSignalingRepository() }
    private var currentIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntentState = intent

        // Turn screen on and show over lockscreen for call alerts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleIntent(intent)

        if (authRepository.isSimRegistered()) {
            com.videocalls.app.service.CallListenerService.start(this)
        }

        // Global back interceptor: back gesture or button must NEVER terminate an active call screen
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (com.videocalls.app.VideoCallApp.isInActiveCall) {
                    // Intercept and swallow back press entirely during active calls
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        setContent {
            VideoCallsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentUser by authRepository.authStateFlow().collectAsState(initial = auth.currentUser)
                    val isRegistered = authRepository.isSimRegistered()
                    val startDest = if (isRegistered) Screen.Home.route else Screen.SimIdentity.route

                    // Explicit runtime permission request for Android 13+ (API 33+)
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted && authRepository.isSimRegistered()) {
                            com.videocalls.app.service.CallListenerService.start(this@MainActivity)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasNotif = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasNotif) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    AppNavGraph(
                        navController = navController,
                        currentUid = currentUser?.uid,
                        startDestination = startDest
                    )

                    // Real-time listener for incoming calls while app is active
                    val activeUid = currentUser?.uid
                    LaunchedEffect(activeUid) {
                        if (authRepository.isSimRegistered()) {
                            authRepository.ensureProfileInFirestore()
                        }
                    }

                    val myPhone = authRepository.getLocalPhoneNumber()
                    LaunchedEffect(myPhone, activeUid) {
                        signalingRepo.observeIncomingCalls(receiverPhone = myPhone, receiverUid = activeUid).collect { incomingCall ->
                            if (incomingCall != null && incomingCall.status == CallStatus.RINGING) {
                                val callDisplayName = incomingCall.callerName.ifBlank {
                                    if (incomingCall.isGroupCall) "Group Call" else "Caller"
                                }
                                // When app is open, handle call in-app directly and ensure notification is dismissed
                                CallNotificationHelper.cancelNotification(this@MainActivity, incomingCall.id)
                                val currentRoute = navController.currentDestination?.route
                                if (currentRoute == Screen.Home.route || currentRoute == null) {
                                    navController.navigate(
                                        Screen.IncomingCall.createRoute(
                                            callId = incomingCall.id,
                                            callerName = callDisplayName,
                                            callerPhone = incomingCall.callerPhone,
                                            callType = incomingCall.callType.name,
                                            isGroupCall = incomingCall.isGroupCall,
                                            groupId = incomingCall.groupId
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Handle intent navigations (e.g. from notifications)
                    LaunchedEffect(currentIntentState) {
                        checkIntentNavigation(currentIntentState) { route ->
                            navController.navigate(route)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        currentIntentState = intent
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val callId = intent.getStringExtra("callId") ?: ""

        if (action == ACTION_DECLINE_CALL && callId.isNotBlank()) {
            CallNotificationHelper.cancelNotification(this, callId)
            // Reject call in firestore
            Thread {
                kotlinx.coroutines.runBlocking {
                    signalingRepo.updateCallStatus(callId, CallStatus.REJECTED)
                }
            }.start()
        }
    }

    private fun checkIntentNavigation(targetIntent: Intent?, navigate: (String) -> Unit) {
        if (targetIntent == null) return
        val navTo = targetIntent.getStringExtra(EXTRA_NAVIGATE_TO)
        val callId = targetIntent.getStringExtra("callId") ?: return

        if (navTo == "incoming_call" || targetIntent.action == ACTION_ACCEPT_CALL) {
            val callerName = targetIntent.getStringExtra("callerName") ?: "Caller"
            val callerPhone = targetIntent.getStringExtra("callerPhone") ?: ""
            val callType = targetIntent.getStringExtra("callType") ?: "VIDEO"
            val isGroupCall = targetIntent.getBooleanExtra("isGroupCall", false)
            val groupId = targetIntent.getStringExtra("groupId")

            targetIntent.removeExtra(EXTRA_NAVIGATE_TO)
            targetIntent.removeExtra("callId")

            if (targetIntent.action == ACTION_ACCEPT_CALL) {
                if (isGroupCall && !groupId.isNullOrBlank()) {
                    navigate(Screen.GroupCall.createRoute(callId, groupId, callerName, callType == "VIDEO"))
                } else {
                    navigate(Screen.SingleCall.createRoute(callId, false, callerName, callerPhone, callType == "VIDEO"))
                }
            } else {
                navigate(Screen.IncomingCall.createRoute(callId, callerName, callerPhone, callType, isGroupCall, groupId))
            }
        }
    }

    companion object {
        const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"
        const val ACTION_ACCEPT_CALL = "com.videocalls.app.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.videocalls.app.ACTION_DECLINE_CALL"
    }
}
