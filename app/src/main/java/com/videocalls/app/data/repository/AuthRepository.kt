package com.videocalls.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.model.User
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val context: Context = VideoCallApp.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("videocalls_identity", Context.MODE_PRIVATE)
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    fun isSimRegistered(): Boolean {
        val phone = getLocalPhoneNumber()
        return phone.isNotBlank() && currentUser != null
    }

    fun getLocalPhoneNumber(): String {
        val raw = prefs.getString("sim_phone_number", "") ?: ""
        return PhoneNumberHelper.cleanIndianNumber(raw)
    }

    fun getLocalDisplayName(): String {
        return prefs.getString("sim_display_name", "") ?: ""
    }

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { authState ->
            trySend(authState.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun bindSimIdentity(phoneNumber: String, displayName: String = "User"): User {
        // Ensure authenticated with Firebase
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Failed to authenticate Firebase user")
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(phoneNumber)

        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            ""
        }

        // Search for any existing user records with this phone number using where query
        val existingDocs = try {
            firestore.collection("users")
                .whereEqualTo("phoneNumber", cleanPhone)
                .get()
                .await()
                .documents
        } catch (e: Exception) {
            emptyList()
        }

        val targetDocRef = if (existingDocs.isNotEmpty()) {
            val primaryDoc = existingDocs.first()
            // Clean up any extra duplicate docs for the same number
            if (existingDocs.size > 1) {
                for (duplicate in existingDocs.drop(1)) {
                    try { duplicate.reference.delete().await() } catch (_: Exception) {}
                }
            }
            primaryDoc.reference
        } else {
            // New user: standard document ID using Firebase Auth UID (NOT phone number)
            firestore.collection("users").document(uid)
        }

        val existingSnapshot = try { targetDocRef.get().await() } catch (e: Exception) { null }
        val existingName = existingSnapshot?.getString("displayName")
        val finalName = when {
            displayName.isNotBlank() && displayName != "User" -> displayName
            !existingName.isNullOrBlank() -> existingName
            else -> "User ${cleanPhone.takeLast(4)}"
        }

        val user = User(
            uid = uid,
            phoneNumber = cleanPhone,
            displayName = finalName,
            fcmToken = fcmToken,
            avatarUrl = existingSnapshot?.getString("avatarUrl") ?: "",
            createdAt = existingSnapshot?.getDate("createdAt") ?: java.util.Date(),
            lastSeen = java.util.Date()
        )

        targetDocRef.set(user, SetOptions.merge()).await()

        prefs.edit()
            .putString("sim_phone_number", cleanPhone)
            .putString("sim_display_name", finalName)
            .apply()

        return user
    }

    suspend fun saveUserProfile(displayName: String, phoneNumber: String): User {
        return bindSimIdentity(phoneNumber, displayName)
    }

    suspend fun updateFcmToken(token: String) {
        val phone = getLocalPhoneNumber()
        val uid = currentUserId
        if (uid != null) {
            try {
                firestore.collection("users").document(uid).update("fcmToken", token).await()
            } catch (_: Exception) {}
        }
        if (phone.isNotBlank()) {
            try {
                val matches = firestore.collection("users")
                    .whereEqualTo("phoneNumber", phone)
                    .get()
                    .await()
                for (doc in matches.documents) {
                    try { doc.reference.update("fcmToken", token).await() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getCurrentUserProfile(): User? {
        val uid = currentUserId
        if (uid != null) {
            val snapshot = firestore.collection("users").document(uid).get().await()
            if (snapshot.exists()) {
                val u = snapshot.toObject(User::class.java)
                if (u != null) return u
            }
        }
        val phone = getLocalPhoneNumber()
        if (phone.isNotBlank()) {
            val snapshot = firestore.collection("users")
                .whereEqualTo("phoneNumber", phone)
                .limit(1)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                return snapshot.documents.first().toObject(User::class.java)
            }
        }
        return null
    }

    suspend fun ensureProfileInFirestore(): User? {
        val phone = getLocalPhoneNumber()
        if (phone.isBlank()) return null

        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                return null
            }
        }
        val name = getLocalDisplayName()
        return try {
            bindSimIdentity(phone, name)
        } catch (e: Exception) {
            null
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        auth.signOut()
    }
}
