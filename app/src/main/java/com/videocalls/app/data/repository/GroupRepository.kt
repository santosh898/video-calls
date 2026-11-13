package com.videocalls.app.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.local.AppDatabaseHelper
import com.videocalls.app.data.model.Group
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GroupRepository(
    private val context: Context = VideoCallApp.instance,
    private val dbHelper: AppDatabaseHelper = AppDatabaseHelper.getInstance(context),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun createGroup(
        name: String,
        creatorUid: String,
        memberUids: List<String>,
        memberPhones: List<String>
    ): String {
        val allMemberUids = (memberUids + creatorUid).distinct()
        val cleanedPhones = memberPhones.map { PhoneNumberHelper.cleanIndianNumber(it) }.distinct()
        val groupRef = firestore.collection("groups").document()
        val group = Group(
            id = groupRef.id,
            name = name,
            createdBy = creatorUid,
            memberUids = allMemberUids,
            memberPhones = cleanedPhones
        )
        groupRef.set(group).await()
        return groupRef.id
    }

    /**
     * Local-first reactive stream: emits SQLite cached groups immediately,
     * updates live from Firestore snapshot listener.
     */
    fun observeUserGroups(currentUid: String): Flow<List<Group>> = callbackFlow {
        // 1. Emit local SQLite cache immediately
        val cached = dbHelper.getAllGroups()
        if (cached.isNotEmpty()) {
            trySend(cached)
        }

        // 2. Realtime Firestore listener
        val listener = firestore.collection("groups")
            .whereArrayContains("memberUids", currentUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { it.toObject(Group::class.java) } ?: emptyList()
                trySend(groups)
                scope.launch {
                    dbHelper.saveGroups(groups)
                }
            }
        awaitClose { listener.remove() }
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
