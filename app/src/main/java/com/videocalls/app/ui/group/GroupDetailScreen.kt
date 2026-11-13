package com.videocalls.app.ui.group

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.data.repository.GroupRepository
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    currentUid: String,
    onNavigateBack: () -> Unit,
    onStartGroupCall: (groupId: String, groupName: String, isVideo: Boolean) -> Unit,
    onJoinActiveCall: (callId: String, groupId: String, groupName: String) -> Unit,
    groupRepository: GroupRepository = remember { GroupRepository() },
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val group by groupRepository.observeGroup(groupId).collectAsState(initial = null)
    val myPhone = remember { PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber()) }

    var showActionDialog by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }

    val isCreator = remember(group, currentUid, myPhone) {
        val grp = group
        if (grp == null) false
        else (grp.createdBy.isNotBlank() && grp.createdBy == currentUid) ||
             (grp.creatorPhone.isNotBlank() && grp.creatorPhone == myPhone)
    }

    if (showActionDialog && group != null) {
        val grp = group!!
        AlertDialog(
            onDismissRequest = { if (!isOperating) showActionDialog = false },
            icon = {
                Icon(
                    imageVector = if (isCreator) Icons.Rounded.Delete else Icons.AutoMirrored.Rounded.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (isCreator) "Delete Group?" else "Leave Group?") },
            text = {
                Text(
                    if (isCreator) "Are you sure you want to delete '${grp.name}'? This will permanently delete the group for all members."
                    else "Are you sure you want to leave '${grp.name}'? You will no longer be a member of this group."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        coroutineScope.launch {
                            try {
                                if (isCreator) {
                                    groupRepository.deleteGroup(groupId)
                                    Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                                } else {
                                    groupRepository.leaveGroup(groupId, currentUid, myPhone)
                                    Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                                }
                                isOperating = false
                                showActionDialog = false
                                onNavigateBack()
                            } catch (e: Exception) {
                                isOperating = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isOperating
                ) {
                    if (isOperating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (isCreator) "Delete" else "Leave", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showActionDialog = false },
                    enabled = !isOperating
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (group != null) {
                        IconButton(onClick = { showActionDialog = true }) {
                            Icon(
                                imageVector = if (isCreator) Icons.Rounded.Delete else Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = if (isCreator) "Delete Group" else "Leave Group",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val grp = group!!
            val hasActiveCall = !grp.activeCallId.isNullOrBlank()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
            ) {
                // Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = grp.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${grp.memberPhones.size} members" + if (isCreator) " • You are Admin" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (hasActiveCall) {
                            Button(
                                onClick = { grp.activeCallId?.let { onJoinActiveCall(it, grp.id, grp.name) } },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Join Live Call", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { onStartGroupCall(grp.id, grp.name, true) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Videocam, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Video Call")
                                }

                                OutlinedButton(
                                    onClick = { onStartGroupCall(grp.id, grp.name, false) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Audio Call")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "MEMBERS (${grp.memberPhones.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(grp.memberPhones) { phone ->
                        val isSelf = phone == myPhone
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (isSelf) "You (${PhoneNumberHelper.formatForDisplay(phone)})" else PhoneNumberHelper.formatForDisplay(phone),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = if (phone == grp.creatorPhone || (grp.createdBy == currentUid && isSelf)) {
                                { Text("Group Creator", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Button: Delete Group (Creator) OR Leave Group (Member)
                OutlinedButton(
                    onClick = { showActionDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (isCreator) Icons.Rounded.Delete else Icons.AutoMirrored.Rounded.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCreator) "Delete Group" else "Leave Group",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
