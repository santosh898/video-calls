package com.videocalls.app.data.repository

import android.content.Context
import com.videocalls.app.data.local.AppDatabaseHelper
import com.videocalls.app.data.local.LiveSyncManager
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactsRepository(
    private val context: Context,
    private val dbHelper: AppDatabaseHelper = AppDatabaseHelper.getInstance(context),
    private val liveSyncManager: LiveSyncManager = LiveSyncManager.getInstance(context),
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    init {
        // Ensure real-time Firestore synchronization is running
        liveSyncManager.startLiveSync()
    }

    /**
     * Local-first reactive stream: emits cached SQLite contacts INSTANTLY on mount,
     * then automatically re-emits when background sync or live Firestore updates arrive.
     */
    fun observeContacts(currentUserId: String? = null): Flow<List<ContactItem>> {
        val myPhone = PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber())
        return dbHelper.contactsFlow.map { list ->
            list.filter { item ->
                item.normalizedPhoneNumber != myPhone &&
                item.registeredUser?.uid != currentUserId
            }
        }
    }

    /**
     * One-shot fetch from local SQLite database (0ms latency, no network block)
     */
    suspend fun getCachedContacts(currentUserId: String? = null): List<ContactItem> {
        val myPhone = PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber())
        return dbHelper.getAllContacts().filter { item ->
            item.normalizedPhoneNumber != myPhone &&
            item.registeredUser?.uid != currentUserId
        }
    }

    /**
     * Complete refresh cycle:
     * 1. Guarantees this device's profile is published to Firestore (even if DB was wiped)
     * 2. Scans local device contacts into SQLite
     * 3. Fetches all registered users from Firestore into SQLite
     */
    suspend fun refreshContacts() {
        authRepository.ensureProfileInFirestore()
        liveSyncManager.syncDeviceContacts()
        liveSyncManager.fetchRegisteredUsersNow()
    }
}
