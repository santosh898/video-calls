package com.videocalls.app.ui.auth

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.data.sim.SimManager
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.launch

@Composable
fun SimIdentityScreen(
    onIdentityBound: () -> Unit,
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val simManager = remember { SimManager(context) }

    var statusMessage by remember { mutableStateOf("Detecting SIM card…") }
    var detectedPhone by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showManualFallback by remember { mutableStateOf(false) }
    var manualPhoneInput by remember { mutableStateOf("") }

    fun completeBinding(phone: String, name: String) {
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(phone)
        isProcessing = true
        statusMessage = "Setting up profile for ${PhoneNumberHelper.formatForDisplay(cleanPhone)}…"
        coroutineScope.launch {
            try {
                authRepository.bindSimIdentity(
                    phoneNumber = cleanPhone,
                    displayName = name.ifBlank { "User" }
                )
                isProcessing = false
                com.videocalls.app.service.CallListenerService.start(context)
                onIdentityBound()
            } catch (e: Exception) {
                isProcessing = false
                statusMessage = "Failed to setup profile: ${e.message}"
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Play Services Phone Number Hint Launcher (for 1-tap SIM card bottom sheet)
    val phoneHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val phone = simManager.parsePhoneNumberFromHint(result.data)
            if (!phone.isNullOrBlank()) {
                detectedPhone = phone
                completeBinding(phone, displayName)
            } else {
                statusMessage = "Could not verify SIM. Please enter number below."
                showManualFallback = true
            }
        } else {
            statusMessage = "SIM selection cancelled."
            showManualFallback = true
        }
    }

    // Permission launcher for READ_PHONE_NUMBERS / READ_PHONE_STATE
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            val phone = simManager.detectSimPhoneNumber()
            if (!phone.isNullOrBlank()) {
                detectedPhone = phone
                completeBinding(phone, displayName)
            } else if (activity != null) {
                // SIM chip is blank (common carrier default) -> prompt 1-tap Google SIM sheet
                statusMessage = "Requesting SIM number…"
                simManager.requestPhoneNumberHint(
                    activity = activity,
                    onIntentSenderReady = { intentSenderRequest ->
                        phoneHintLauncher.launch(intentSenderRequest)
                    },
                    onError = {
                        statusMessage = "Could not auto-read SIM."
                        showManualFallback = true
                    }
                )
            } else {
                showManualFallback = true
            }
        } else if (activity != null) {
            // Permission denied -> fallback to Google SIM sheet (which does not require phone state permission!)
            simManager.requestPhoneNumberHint(
                activity = activity,
                onIntentSenderReady = { intentSenderRequest ->
                    phoneHintLauncher.launch(intentSenderRequest)
                },
                onError = {
                    showManualFallback = true
                }
            )
        } else {
            showManualFallback = true
        }
    }

    // Auto-detect on screen entry
    LaunchedEffect(Unit) {
        if (simManager.hasPhonePermission()) {
            val phone = simManager.detectSimPhoneNumber()
            if (!phone.isNullOrBlank()) {
                detectedPhone = phone
                completeBinding(phone, displayName)
            } else if (activity != null) {
                simManager.requestPhoneNumberHint(
                    activity = activity,
                    onIntentSenderReady = { phoneHintLauncher.launch(it) },
                    onError = { showManualFallback = true }
                )
            } else {
                showManualFallback = true
            }
        } else {
            // Request permissions or trigger Google SIM hint
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_PHONE_NUMBERS,
                    Manifest.permission.READ_PHONE_STATE
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // SIM Card Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SimCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SIM Identity",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                } else if (!showManualFallback) {
                    Button(
                        onClick = {
                            if (activity != null) {
                                simManager.requestPhoneNumberHint(
                                    activity = activity,
                                    onIntentSenderReady = { phoneHintLauncher.launch(it) },
                                    onError = { showManualFallback = true }
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Verify with SIM Card")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { showManualFallback = true }) {
                        Text("Enter manually")
                    }
                } else {
                    // Fallback for emulators or devices without a cellular SIM
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = manualPhoneInput,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(10)
                            manualPhoneInput = digits
                        },
                        label = { Text("Mobile Number") },
                        prefix = { Text("+91 ") },
                        placeholder = { Text("98765 43210") },
                        leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val clean = PhoneNumberHelper.cleanIndianNumber(manualPhoneInput)
                            if (PhoneNumberHelper.isValidIndianNumber(clean)) {
                                completeBinding(clean, displayName)
                            } else {
                                Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
