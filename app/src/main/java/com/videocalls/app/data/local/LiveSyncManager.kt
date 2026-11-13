package com.videocalls.app.data.local

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.model.User
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

private const val TAG = "LiveSyncManager"

class LiveSyncManager(
    private val context: Context,
    private val dbHelper: AppDatabaseHelper = AppDatabaseHelper.getInstance(context),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        @Volatile
        private var instance: LiveSyncManager? = null

        fun getInstance(context: Context): LiveSyncManager {
            return instance ?: synchronized(this) {
                instance ?: LiveSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var usersListener: ListenerRegistration? = null

    fun startLiveSync() {
        if (usersListener != null) return

        if (auth.currentUser == null) {
            scope.launch {
                try {
                    auth.signInAnonymously().await()
                    attachUsersListener()
                } catch (e: Exception) {
                    Log.e(TAG, "Error signing in for live sync", e)
                }
            }
        } else {
            attachUsersListener()
        }
    }

    private fun attachUsersListener() {
        if (usersListener != null) return

        usersListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in users live snapshot", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                    scope.launch {
                        dbHelper.updateRegisteredUsers(users)
                    }
                }
            }
    }

    fun stopLiveSync() {
        usersListener?.remove()
        usersListener = null
    }

    /**
     * Direct one-shot fetch from Firestore to immediately populate SQLite registered users
     */
    suspend fun fetchRegisteredUsersNow(): Unit = withContext(Dispatchers.IO) {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                Log.e(TAG, "Auth failed before fetching registered users", e)
                return@withContext
            }
        }

        try {
            val snapshot = firestore.collection("users").get().await()
            val users = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
            dbHelper.updateRegisteredUsers(users)
            attachUsersListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching registered users directly", e)
        }
    }

    /**
     * Reads device contacts in background and updates local SQLite database
     */
    suspend fun syncDeviceContacts(): Unit = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor: Cursor? = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Permission missing or failed to query contacts", e)
            null
        }

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            val seenNumbers = mutableSetOf<String>()

            while (it.moveToNext()) {
                val id = if (idIndex != -1) it.getString(idIndex) ?: "" else ""
                val name = if (nameIndex != -1) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                val rawNumber = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""

                val cleanNumber = PhoneNumberHelper.cleanIndianNumber(rawNumber)
                if (PhoneNumberHelper.isValidIndianNumber(cleanNumber) && seenNumbers.add(cleanNumber)) {
                    contacts.add(
                        ContactItem(
                            contactId = id.ifBlank { cleanNumber },
                            displayName = name,
                            rawPhoneNumber = rawNumber,
                            normalizedPhoneNumber = cleanNumber
                        )
                    )
                }
            }
        }

        if (contacts.isNotEmpty()) {
            dbHelper.saveDeviceContacts(contacts)
        }
    }
}
