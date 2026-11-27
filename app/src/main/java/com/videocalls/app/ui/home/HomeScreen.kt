package com.videocalls.app.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import android.widget.Toast
import com.videocalls.app.BuildConfig
import com.videocalls.app.data.model.CallType
import com.videocalls.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUid: String,
    onStartSingleCall: (targetUid: String, targetName: String, targetPhone: String, callType: CallType) -> Unit,
    onOpenGroupDetail: (groupId: String) -> Unit,
    onStartGroupCall: (groupId: String, groupName: String, isVideo: Boolean) -> Unit,
    onJoinActiveCall: (callId: String, groupId: String, groupName: String) -> Unit,
    onCreateGroupClick: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSecretTestScreen: () -> Unit = {},
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val context = LocalContext.current

    var areNotificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        areNotificationsEnabled = isGranted
        if (isGranted) {
            com.videocalls.app.service.CallListenerService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    Toast.makeText(context, "🧪 Opening Feature Testing Lab...", Toast.LENGTH_SHORT).show()
                                    onOpenSecretTestScreen()
                                }
                            )
                        }
                    ) {
                        Text(
                            text = "VideoCalls",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "v${BuildConfig.SEMVER_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        authRepository.signOut()
                        onSignOut()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign Out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 1) {
                FloatingActionButton(
                    onClick = onCreateGroupClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = "Create Group")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!areNotificationsEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notifications Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Allow notifications to receive call alerts in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Enable", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    },
                    text = { Text("Contacts", fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Rounded.Call, contentDescription = null) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    text = { Text("Groups", fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Rounded.Groups, contentDescription = null) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ContactsListTab(
                        currentUid = currentUid,
                        onStartCall = onStartSingleCall
                    )
                    1 -> GroupsTab(
                        currentUid = currentUid,
                        onOpenGroupDetail = onOpenGroupDetail,
                        onStartGroupCall = onStartGroupCall,
                        onJoinActiveCall = onJoinActiveCall
                    )
                }
            }
        }
    }
}
