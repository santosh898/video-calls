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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.repository.ContactsRepository
import com.videocalls.app.data.repository.GroupRepository
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    currentUid: String,
    onNavigateBack: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit,
    groupRepository: GroupRepository = remember { GroupRepository() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contactsRepository = remember { ContactsRepository(context) }

    var groupName by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    val selectedContacts = remember { mutableStateListOf<ContactItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        // Only registered app users can be added to WebRTC video call groups
        contacts = contactsRepository.getCachedContacts(currentUid).filter { it.isRegistered }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (groupName.trim().isBlank()) {
                                Toast.makeText(context, "Please enter a group name", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            if (selectedContacts.isEmpty()) {
                                Toast.makeText(context, "Select at least 1 contact", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            isSubmitting = true
                            coroutineScope.launch {
                                try {
                                    val memberUids = selectedContacts.mapNotNull { it.registeredUser?.uid }
                                    val memberPhones = selectedContacts.map { it.normalizedPhoneNumber }
                                    val groupId = groupRepository.createGroup(
                                        name = groupName.trim(),
                                        creatorUid = currentUid,
                                        memberUids = memberUids,
                                        memberPhones = memberPhones
                                    )
                                    isSubmitting = false
                                    onGroupCreated(groupId)
                                } catch (e: Exception) {
                                    isSubmitting = false
                                    Toast.makeText(context, "Error creating group: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSubmitting && groupName.isNotBlank() && selectedContacts.isNotEmpty()
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                placeholder = { Text("e.g. Design Team, Family") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Selected count badge / limit info
            Text(
                text = "Add participants (${selectedContacts.size}/7 selected · Max 8 per call)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "None of your contacts are currently registered on VideoCalls.\nWhen someone in your contacts installs the app, they'll appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts, key = { it.contactId }) { contact ->
                        val isSelected = selectedContacts.any { it.contactId == contact.contactId }
                        ListItem(
                            modifier = Modifier.clickable {
                                if (isSelected) {
                                    selectedContacts.removeAll { it.contactId == contact.contactId }
                                } else {
                                    if (selectedContacts.size >= 7) {
                                        Toast.makeText(context, "Max 8 participants per group (including you)", Toast.LENGTH_SHORT).show()
                                    } else {
                                        selectedContacts.add(contact)
                                    }
                                }
                            },
                            headlineContent = {
                                Text(contact.displayName, fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(PhoneNumberHelper.formatForDisplay(contact.normalizedPhoneNumber))
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null // handled by ListItem click
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
