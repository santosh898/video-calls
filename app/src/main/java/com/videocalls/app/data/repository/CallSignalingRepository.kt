package com.videocalls.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.videocalls.app.data.model.*
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

private const val TAG = "CallSignalingRepo"

class CallSignalingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ----------------------------------------------------
    // Call Session Management
    // ----------------------------------------------------

    suspend fun createSingleCall(
        callerId: String,
        callerName: String,
        callerPhone: String,
        receiverId: String,
        receiverPhone: String,
        callType: CallType
    ): String {
        val cleanCallerPhone = PhoneNumberHelper.cleanIndianNumber(callerPhone)
        val cleanReceiverPhone = PhoneNumberHelper.cleanIndianNumber(receiverPhone)
        val callRef = firestore.collection("calls").document()
        val session = CallSession(
            id = callRef.id,
            callerId = callerId,
            callerName = callerName,
            callerPhone = cleanCallerPhone,
            receiverId = receiverId,
            receiverPhone = cleanReceiverPhone,
            isGroupCall = false,
            callType = callType,
            status = CallStatus.RINGING,
            activeParticipants = listOf(callerId)
        )
        callRef.set(session).await()
        return callRef.id
    }

    suspend fun createGroupCall(
        callerId: String,
        callerName: String,
        callerPhone: String,
        groupId: String,
        callType: CallType
    ): String {
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(callerPhone)

        // Fetch group to determine target phones and UIDs for incoming ring
        var targetPhones = emptyList<String>()
        var targetUids = emptyList<String>()
        try {
            val groupDoc = firestore.collection("groups").document(groupId).get().await()
            val memberPhones = (groupDoc.get("memberPhones") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val memberUids = (groupDoc.get("memberUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            targetPhones = memberPhones.map { PhoneNumberHelper.cleanIndianNumber(it) }.filter { it.isNotBlank() && it != cleanPhone }
            targetUids = memberUids.filter { it.isNotBlank() && it != callerId }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching group members for call signaling: $groupId", e)
        }

        val callRef = firestore.collection("calls").document()
        val session = CallSession(
            id = callRef.id,
            callerId = callerId,
            callerName = callerName,
            callerPhone = cleanPhone,
            groupId = groupId,
            isGroupCall = true,
            callType = callType,
            status = CallStatus.RINGING,
            activeParticipants = listOf(callerId),
            targetPhones = targetPhones,
            targetUids = targetUids
        )
        callRef.set(session).await()

        firestore.collection("groups").document(groupId)
            .update("activeCallId", callRef.id)
            .await()

        return callRef.id
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing call session $callId", error)
                    return@addSnapshotListener
                }
                val session = snapshot?.toObject(CallSession::class.java)
                trySend(session)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Listens in real-time for incoming calls directed to this user:
     * 1. 1-to-1 calls targeting phone or UID
     * 2. Group calls targeting phone (targetPhones) or UID (targetUids)
     */
    fun observeIncomingCalls(receiverPhone: String, receiverUid: String? = null): Flow<CallSession?> = callbackFlow {
        val cleanPhone = PhoneNumberHelper.cleanIndianNumber(receiverPhone)
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        val activeIncomingCalls = java.util.concurrent.ConcurrentHashMap<String, CallSession>()

        fun emitLatestRinging() {
            val ringingCall = activeIncomingCalls.values
                .filter { it.status == CallStatus.RINGING }
                .maxByOrNull { it.createdAt?.time ?: 0L }
            trySend(ringingCall)
        }

        fun handleSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?, tag: String) {
            if (snapshot == null) return
            for (change in snapshot.documentChanges) {
                val call = change.document.toObject(CallSession::class.java)
                when (change.type) {
                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                        if (call.status == CallStatus.RINGING) {
                            activeIncomingCalls[call.id] = call
                        } else {
                            activeIncomingCalls.remove(call.id)
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                        activeIncomingCalls.remove(call.id)
                    }
                }
            }
            emitLatestRinging()
        }

        if (cleanPhone.length == 10) {
            // Direct 1-on-1 incoming calls
            val lDirectPhone = firestore.collection("calls")
                .whereEqualTo("receiverPhone", cleanPhone)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "Error in direct phone incoming listener", err)
                        return@addSnapshotListener
                    }
                    handleSnapshot(snap, "direct-phone")
                }
            listeners.add(lDirectPhone)

            // Group incoming calls
            val lGroupPhone = firestore.collection("calls")
                .whereArrayContains("targetPhones", cleanPhone)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "Error in group phone incoming listener", err)
                        return@addSnapshotListener
                    }
                    handleSnapshot(snap, "group-phone")
                }
            listeners.add(lGroupPhone)
        }

        if (!receiverUid.isNullOrBlank()) {
            val lDirectUid = firestore.collection("calls")
                .whereEqualTo("receiverId", receiverUid)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "Error in direct uid incoming listener", err)
                        return@addSnapshotListener
                    }
                    handleSnapshot(snap, "direct-uid")
                }
            listeners.add(lDirectUid)

            val lGroupUid = firestore.collection("calls")
                .whereArrayContains("targetUids", receiverUid)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.e(TAG, "Error in group uid incoming listener", err)
                        return@addSnapshotListener
                    }
                    handleSnapshot(snap, "group-uid")
                }
            listeners.add(lGroupUid)
        }

        if (listeners.isEmpty()) {
            trySend(null)
        }

        awaitClose {
            listeners.forEach { it.remove() }
            listeners.clear()
            activeIncomingCalls.clear()
        }
    }

    suspend fun updateCallStatus(callId: String, status: CallStatus) {
        try {
            firestore.collection("calls").document(callId)
                .update("status", status.name)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call status $callId to $status", e)
        }
    }

    suspend fun joinCall(callId: String, uid: String) {
        try {
            val callRef = firestore.collection("calls").document(callId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                val session = snapshot.toObject(CallSession::class.java) ?: return@runTransaction
                val updatedParticipants = (session.activeParticipants + uid).distinct()
                val newMax = maxOf(session.maxParticipantsReached, updatedParticipants.size)
                transaction.update(
                    callRef,
                    mapOf(
                        "activeParticipants" to FieldValue.arrayUnion(uid),
                        "status" to CallStatus.CONNECTED.name,
                        "maxParticipantsReached" to newMax
                    )
                )
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error joining call $callId", e)
        }
    }

    suspend fun leaveCall(callId: String, uid: String, groupId: String? = null) {
        try {
            val callRef = firestore.collection("calls").document(callId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                val session = snapshot.toObject(CallSession::class.java) ?: return@runTransaction
                val remaining = session.activeParticipants.filter { it != uid }
                val targetGroupId = groupId ?: session.groupId

                // Auto-close condition:
                // 1. Nobody left in the call (0 participants).
                // 2. OR at least 2 people had joined previously, and now only 1 (or 0) person remains.
                val shouldEnd = remaining.isEmpty() || (session.maxParticipantsReached >= 2 && remaining.size <= 1)

                if (shouldEnd) {
                    transaction.update(
                        callRef,
                        mapOf(
                            "activeParticipants" to remaining,
                            "status" to CallStatus.ENDED.name
                        )
                    )
                    if (!targetGroupId.isNullOrBlank()) {
                        transaction.update(
                            firestore.collection("groups").document(targetGroupId),
                            "activeCallId", null
                        )
                    }
                } else {
                    transaction.update(callRef, "activeParticipants", remaining)
                }
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving call $callId", e)
        }
    }

    // ----------------------------------------------------
    // Robust 1-to-1 WebRTC Signaling Under 100 Lines
    // ----------------------------------------------------

    suspend fun sendOffer(callId: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "offer",
            "sdp" to sdp.description,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer")
            .set(signal, SetOptions.merge())
            .await()
    }

    fun observeOffer(callId: String): Flow<SessionDescription?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val sdpStr = snapshot.getString("sdp")
                if (!sdpStr.isNullOrBlank()) {
                    trySend(SessionDescription(SessionDescription.Type.OFFER, sdpStr))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendAnswer(callId: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "answer",
            "sdp" to sdp.description,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer")
            .set(signal, SetOptions.merge())
            .await()
    }

    fun observeAnswer(callId: String): Flow<SessionDescription?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val sdpStr = snapshot.getString("sdp")
                if (!sdpStr.isNullOrBlank()) {
                    trySend(SessionDescription(SessionDescription.Type.ANSWER, sdpStr))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendCandidate(callId: String, isCaller: Boolean, candidate: IceCandidate) {
        val collName = if (isCaller) "callerCandidates" else "receiverCandidates"
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection(collName)
            .add(data)
            .await()
    }

    fun observeRemoteCandidates(callId: String, isCaller: Boolean): Flow<IceCandidate> = callbackFlow {
        val collName = if (isCaller) "receiverCandidates" else "callerCandidates"
        val listener = firestore.collection("calls").document(callId)
            .collection(collName)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    val data = change.document.data
                    val sdp = data["sdp"] as? String ?: ""
                    val sdpMid = data["sdpMid"] as? String ?: ""
                    val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                    if (sdp.isNotBlank()) {
                        trySend(IceCandidate(sdpMid, sdpMLineIndex, sdp))
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    // ----------------------------------------------------
    // Multi-Peer Group Call Signaling Overloads
    // ----------------------------------------------------

    suspend fun sendOffer(callId: String, senderUid: String, targetUid: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "offer",
            "sdp" to sdp.description,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("offer_${senderUid}_$targetUid")
            .set(signal, SetOptions.merge())
            .await()
    }

    suspend fun sendAnswer(callId: String, senderUid: String, targetUid: String, sdp: SessionDescription) {
        val signal = hashMapOf(
            "type" to "answer",
            "sdp" to sdp.description,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("signals")
            .document("answer_${senderUid}_$targetUid")
            .set(signal, SetOptions.merge())
            .await()
    }

    suspend fun sendIceCandidate(callId: String, senderUid: String, targetUid: String, candidate: IceCandidate) {
        val data = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "senderUid" to senderUid,
            "targetUid" to targetUid,
            "timestamp" to FieldValue.serverTimestamp()
        )
        firestore.collection("calls").document(callId)
            .collection("candidates")
            .add(data)
            .await()
    }

    fun observeIncomingSignals(callId: String, currentUid: String): Flow<Map<String, Any>> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("signals")
            .whereEqualTo("targetUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing incoming signals", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    trySend(data)
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeIncomingCandidates(callId: String, currentUid: String): Flow<IceCandidateModel> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .collection("candidates")
            .whereEqualTo("targetUid", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing incoming candidates", error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    val model = IceCandidateModel(
                        sdpMid = data["sdpMid"] as? String ?: "",
                        sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0,
                        sdp = data["sdp"] as? String ?: "",
                        senderUid = data["senderUid"] as? String ?: "",
                        targetUid = data["targetUid"] as? String ?: ""
                    )
                    trySend(model)
                }
            }
        awaitClose { listener.remove() }
    }
}
