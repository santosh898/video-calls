package com.videocalls.app.ui.group

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.data.repository.ContactsRepository
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
    val contactsRepository = remember { ContactsRepository(context) }

    var showActionDialog by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }

    // Rename dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    // Add members dialog state
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var candidateContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    val selectedToAdd = remember { mutableStateListOf<ContactItem>() }
    var isAddingMembers by remember { mutableStateOf(false) }

    // Remove member state
    var memberToRemove by remember { mutableStateOf<String?>(null) }
    var isRemovingMember by remember { mutableStateOf(false) }

    val isCreator = remember(group, currentUid, myPhone) {
        val grp = group
        if (grp == null) false
        else (grp.createdBy.isNotBlank() && grp.createdBy == currentUid) ||
             (grp.creatorPhone.isNotBlank() && grp.creatorPhone == myPhone)
    }

    // 1. Rename Group Dialog
    if (showRenameDialog && group != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            title = { Text("Rename Group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newGroupName.trim()
                        if (trimmed.isNotBlank()) {
                            coroutineScope.launch {
                                groupRepository.updateGroupName(groupId, trimmed)
                                showRenameDialog = false
                                Toast.makeText(context, "Group renamed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = newGroupName.trim().isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Add Members Dialog
    if (showAddMembersDialog && group != null) {
        val grp = group!!
        LaunchedEffect(Unit) {
            val allCached = contactsRepository.getCachedContacts(currentUid)
            candidateContacts = allCached.filter { item ->
                item.isRegistered && !grp.memberPhones.contains(PhoneNumberHelper.cleanIndianNumber(item.normalizedPhoneNumber))
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isAddingMembers) showAddMembersDialog = false },
            icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
            title = { Text("Add Members") },
            text = {
                if (candidateContacts.isEmpty()) {
                    Text("No additional registered contacts available to add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(candidateContacts, key = { it.normalizedPhoneNumber }) { contact ->
                            val isSelected = selectedToAdd.contains(contact)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedToAdd.remove(contact)
                                        else selectedToAdd.add(contact)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedToAdd.add(contact)
                                        else selectedToAdd.remove(contact)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(contact.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        PhoneNumberHelper.formatForDisplay(contact.normalizedPhoneNumber),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isAddingMembers = true
                        coroutineScope.launch {
                            val newPhones = selectedToAdd.map { it.normalizedPhoneNumber }
                            val newUids = selectedToAdd.mapNotNull { it.registeredUser?.uid }
                            groupRepository.addMembers(groupId, newPhones, newUids)
                            isAddingMembers = false
                            showAddMembersDialog = false
                            Toast.makeText(context, "Added ${selectedToAdd.size} member(s)", Toast.LENGTH_SHORT).show()
                            selectedToAdd.clear()
                        }
                    },
                    enabled = !isAddingMembers && selectedToAdd.isNotEmpty()
                ) {
                    if (isAddingMembers) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Add (${selectedToAdd.size})", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMembersDialog = false }, enabled = !isAddingMembers) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Remove Member Dialog
    if (memberToRemove != null && group != null) {
        val phone = memberToRemove!!
        AlertDialog(
            onDismissRequest = { if (!isRemovingMember) memberToRemove = null },
            icon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Member?") },
            text = { Text("Are you sure you want to remove ${PhoneNumberHelper.formatForDisplay(phone)} from this group?") },
            confirmButton = {
                Button(
                    onClick = {
                        isRemovingMember = true
                        coroutineScope.launch {
                            try {
                                val memberUid = contactsRepository.getCachedContacts().find {
                                    PhoneNumberHelper.cleanIndianNumber(it.normalizedPhoneNumber) == phone
                                }?.registeredUser?.uid

                                groupRepository.removeMember(groupId, phone, memberUid)
                                isRemovingMember = false
                                memberToRemove = null
                                Toast.makeText(context, "Member removed", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                isRemovingMember = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isRemovingMember
                ) {
                    if (isRemovingMember) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                    } else {
                        Text("Remove", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }, enabled = !isRemovingMember) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Delete / Leave Group Dialog
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
                Spacer(modifier = Modifier.height(16.dp))

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

                        // Group Name with edit icon for Admin
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = grp.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCreator) {
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        newGroupName = grp.name
                                        showRenameDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Rename Group",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

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

                Spacer(modifier = Modifier.height(20.dp))

                // Members Header with "Add Members" button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MEMBERS (${grp.memberPhones.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    TextButton(
                        onClick = {
                            selectedToAdd.clear()
                            showAddMembersDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = "Add Members",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Member", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(grp.memberPhones) { phone ->
                        val isSelf = phone == myPhone
                        val isGroupCreatorPhone = phone == grp.creatorPhone || (grp.createdBy == currentUid && isSelf)

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = if (isSelf) "You (${PhoneNumberHelper.formatForDisplay(phone)})" else PhoneNumberHelper.formatForDisplay(phone),
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            supportingContent = if (isGroupCreatorPhone) {
                                { Text("Group Admin", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                            } else null,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                // Creator can remove members (except self)
                                if (isCreator && !isSelf && !isGroupCreatorPhone) {
                                    IconButton(
                                        onClick = { memberToRemove = phone },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonRemove,
                                            contentDescription = "Remove Member",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
