package com.videocalls.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.local.AppDatabaseHelper
import com.videocalls.app.data.model.Group
import com.videocalls.app.data.model.User
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GroupRepository"

class GroupRepository(
    private val context: Context = VideoCallApp.instance,
    private val dbHelper: AppDatabaseHelper = AppDatabaseHelper.getInstance(context),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val authRepository: AuthRepository = AuthRepository(context)
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun createGroup(
        name: String,
        creatorUid: String,
        memberUids: List<String>,
        memberPhones: List<String>
    ): String = withContext(Dispatchers.IO) {
        val creatorPhone = PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber())

        // 1. Gather all phone numbers (including creator) and clean to 10 digits
        val allMemberPhones = (memberPhones + creatorPhone)
            .map { PhoneNumberHelper.cleanIndianNumber(it) }
            .filter { it.length == 10 }
            .distinct()

        // 2. Fetch any missing UIDs for these phone numbers from Firestore
        val resolvedUids = memberUids.toMutableSet().apply {
            if (creatorUid.isNotBlank()) add(creatorUid)
        }

        try {
            for (phone in allMemberPhones) {
                val userSnap = firestore.collection("users").document(phone).get().await()
                if (userSnap.exists()) {
                    val u = userSnap.toObject(User::class.java)
                    if (u != null && u.uid.isNotBlank()) {
                        resolvedUids.add(u.uid)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving member UIDs", e)
        }

        val groupRef = firestore.collection("groups").document()
        val group = Group(
            id = groupRef.id,
            name = name,
            createdBy = creatorUid,
            memberUids = resolvedUids.toList(),
            memberPhones = allMemberPhones
        )
        groupRef.set(group).await()

        // Cache immediately in local SQLite
        val currentCached = dbHelper.getAllGroups().toMutableList()
        currentCached.removeAll { it.id == group.id }
        currentCached.add(0, group)
        dbHelper.saveGroups(currentCached)

        groupRef.id
    }

    /**
     * Local-first reactive stream: emits SQLite cached groups immediately,
     * updates live from Firestore snapshot listeners matching user phone OR uid.
     */
    fun observeUserGroups(currentUid: String): Flow<List<Group>> = callbackFlow {
        // 1. Emit local SQLite cache immediately (0ms delay)
        val cached = dbHelper.getAllGroups()
        if (cached.isNotEmpty()) {
            trySend(cached)
        }

        val myPhone = PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber())
        val groupMap = ConcurrentHashMap<String, Group>()
        cached.forEach { groupMap[it.id] = it }

        fun emitSorted() {
            val list = groupMap.values.sortedByDescending { it.createdAt?.time ?: 0L }
            trySend(list)
            scope.launch {
                dbHelper.saveGroups(list)
            }
        }

        val listeners = mutableListOf<ListenerRegistration>()

        // Listener 1: Match by memberPhones (most reliable, survives app reinstall/UID rotation)
        if (myPhone.length == 10) {
            val phoneListener = firestore.collection("groups")
                .whereArrayContains("memberPhones", myPhone)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing groups by phone", error)
                        return@addSnapshotListener
                    }
                    val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                    groups.forEach { groupMap[it.id] = it }
                    emitSorted()
                }
            listeners.add(phoneListener)
        }

        // Listener 2: Match by memberUids
        if (currentUid.isNotBlank()) {
            val uidListener = firestore.collection("groups")
                .whereArrayContains("memberUids", currentUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing groups by UID", error)
                        return@addSnapshotListener
                    }
                    val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                    groups.forEach { groupMap[it.id] = it }
                    emitSorted()
                }
            listeners.add(uidListener)
        }

        awaitClose {
            listeners.forEach { it.remove() }
        }
    }

    suspend fun refreshGroups(currentUid: String): Unit = withContext(Dispatchers.IO) {
        val myPhone = PhoneNumberHelper.cleanIndianNumber(authRepository.getLocalPhoneNumber())
        val groupMap = mutableMapOf<String, Group>()

        if (myPhone.length == 10) {
            try {
                val snap = firestore.collection("groups")
                    .whereArrayContains("memberPhones", myPhone)
                    .get()
                    .await()
                snap.documents.mapNotNull { it.toObject(Group::class.java) }.forEach { groupMap[it.id] = it }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing groups by phone", e)
            }
        }

        if (currentUid.isNotBlank()) {
            try {
                val snap = firestore.collection("groups")
                    .whereArrayContains("memberUids", currentUid)
                    .get()
                    .await()
                snap.documents.mapNotNull { it.toObject(Group::class.java) }.forEach { groupMap[it.id] = it }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing groups by UID", e)
            }
        }

        if (groupMap.isNotEmpty()) {
            val sorted = groupMap.values.sortedByDescending { it.createdAt?.time ?: 0L }
            dbHelper.saveGroups(sorted)
        }
    }

    suspend fun deleteGroup(groupId: String): Unit = withContext(Dispatchers.IO) {
        try {
            // Delete from Firestore
            firestore.collection("groups").document(groupId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting group from Firestore", e)
        }
        // Delete from local SQLite
        dbHelper.deleteGroup(groupId)
    }

    suspend fun getGroupById(groupId: String): Group? {
        val doc = firestore.collection("groups").document(groupId).get().await()
        return doc.toObject(Group::class.java)
    }

    fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val listener = firestore.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, _ ->
                val group = snapshot?.toObject(Group::class.java)
                trySend(group)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateActiveCall(groupId: String, callId: String?) {
        firestore.collection("groups").document(groupId)
            .update("activeCallId", callId)
            .await()
    }
}
