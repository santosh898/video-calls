package com.videocalls.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.videocalls.app.data.model.CallType
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.repository.ContactsRepository
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsListTab(
    currentUid: String,
    onStartCall: (targetUid: String, targetName: String, targetPhone: String, callType: CallType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contactsRepository = remember { ContactsRepository(context) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    // Local-first reactive stream from SQLite - loads instantly on mount!
    val contacts by contactsRepository.observeContacts(currentUid).collectAsState(initial = emptyList())
    var isRefreshing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
        coroutineScope.launch {
            isRefreshing = true
            contactsRepository.refreshContacts()
            isRefreshing = false
        }
    }

    // Refresh automatically on screen mount
    LaunchedEffect(Unit) {
        isRefreshing = true
        contactsRepository.refreshContacts()
        isRefreshing = false
    }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.rawPhoneNumber.contains(searchQuery) ||
                    it.normalizedPhoneNumber.contains(searchQuery)
        }
    }

    val registeredContacts = remember(filteredContacts) { filteredContacts.filter { it.isRegistered } }

    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            coroutineScope.launch {
                isRefreshing = true
                contactsRepository.refreshContacts()
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier.fillMaxSize()
    ) {
        // Single LazyColumn ensures nested scrolling triggers pull-to-refresh reliably even when empty
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Search bar
            item(key = "search_field") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contacts…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (!hasContactsPermission) {
                item(key = "permission_request_card") {
                    PermissionCard(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                    )
                }
            }

            if (registeredContacts.isNotEmpty()) {
                item(key = "registered_contacts_header") {
                    SectionHeader(title = "AVAILABLE ON VIDEOCALLS (${registeredContacts.size})")
                }
                items(registeredContacts, key = { "reg_${it.contactId}_${it.normalizedPhoneNumber}" }) { contact ->
                    RegisteredContactRow(
                        contact = contact,
                        onVideoCall = {
                            onStartCall(
                                contact.registeredUser?.uid ?: "",
                                contact.displayName,
                                contact.normalizedPhoneNumber,
                                CallType.VIDEO
                            )
                        },
                        onAudioCall = {
                            onStartCall(
                                contact.registeredUser?.uid ?: "",
                                contact.displayName,
                                contact.normalizedPhoneNumber,
                                CallType.AUDIO
                            )
                        }
                    )
                }
            } else if (hasContactsPermission) {
                item(key = "empty_contacts_message") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No contacts match \"$searchQuery\""
                                       else "No contacts on VideoCalls yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pull down to refresh and discover users.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun RegisteredContactRow(
    contact: ContactItem,
    onVideoCall: () -> Unit,
    onAudioCall: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = PhoneNumberHelper.formatForDisplay(contact.normalizedPhoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            InitialsAvatar(name = contact.displayName, isRegistered = true)
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onAudioCall) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Audio Call",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                FilledIconButton(
                    onClick = onVideoCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = "Video Call"
                    )
                }
            }
        }
    )
}

@Composable
fun InitialsAvatar(name: String, isRegistered: Boolean) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (isRegistered) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sync Your Contacts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grant contacts access to see which of your friends and family are already using VideoCalls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Allow Contacts Access")
            }
        }
    }
}
